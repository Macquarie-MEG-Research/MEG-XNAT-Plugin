package org.nrg.hcp.importer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.CatCatalog;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatMegsessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.base.BaseXnatResourcecatalog;
import org.nrg.xdat.om.base.auto.AutoXnatImagescandata;
import org.nrg.xnat.helpers.file.StoredFile;
import org.nrg.xnat.helpers.resource.direct.DirectScanResourceImpl;
import org.nrg.xnat.restlet.actions.importer.ImporterHandler;
import org.nrg.xnat.restlet.actions.importer.ImporterHandlerA;
import org.nrg.xnat.restlet.util.FileWriterWrapperI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.services.archive.CatalogService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipOutputStream;

import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xft.utils.zip.TarUtils;
import org.nrg.xft.utils.zip.ZipI;
import org.nrg.xft.utils.zip.ZipUtils;

/**
 * Matches E-prime, physio, eye-tracker and head-tracker files to scans and uploads as a scan resource
 * @author Mike Hodge <hodgem@mir.wustl.edu>
 *
 */
@ImporterHandler(handler = "MEG_LINKED", allowCallsWithoutFiles = true, callPartialUriWrap = false)
public class HCPMEGLinkedDataImporter extends ImporterHandlerA implements Callable<List<String>> {

	static final String[] ZIP_EXT={".zip",".jar",".rar",".ear",".gar",".xar"};
	static final String[] TAR_EXT={".tar",".tgz",".gz"};
	
	static enum ResourceInfo { RESOURCE_LABEL, RESOURCE_FORMAT, RESOURCE_CONTENT, CATXML_PREFIX, CATXML_EXT };
	static enum FileType { EEG, EPRIME };
	static final String LINKED_RESOURCE_LABEL="LINKED_DATA";
	static final Map<ResourceInfo,String> resourceMapLinked;
	static {
		final Map<ResourceInfo,String> map = new HashMap<ResourceInfo,String>();
		map.put(ResourceInfo.RESOURCE_LABEL, LINKED_RESOURCE_LABEL);
		map.put(ResourceInfo.RESOURCE_FORMAT, "MISC");
		map.put(ResourceInfo.RESOURCE_CONTENT, "RAW");
		map.put(ResourceInfo.CATXML_PREFIX, "linkeddata_");
		map.put(ResourceInfo.CATXML_EXT, "_catalog.xml");
		resourceMapLinked = Collections.unmodifiableMap(map);
	}
	static final Map<ResourceInfo,String> resourceMapEEG;
	static {
		final Map<ResourceInfo,String> map = new HashMap<ResourceInfo,String>();
		map.put(ResourceInfo.RESOURCE_LABEL, "EEG");
		map.put(ResourceInfo.RESOURCE_FORMAT, "4D");
		map.put(ResourceInfo.RESOURCE_CONTENT, "RAW");
		map.put(ResourceInfo.CATXML_PREFIX, "eeg_");
		map.put(ResourceInfo.CATXML_EXT, "_catalog.xml");
		resourceMapEEG = Collections.unmodifiableMap(map);
	}
	private Map<String,DirectScanResourceImpl> scanModifiers = new HashMap<String,DirectScanResourceImpl>(); 
    private final Map<String,TreeSet<File>> eprimeTypeMap = new HashMap<String,TreeSet<File>>();
    private final Map<String,TreeSet<XnatImagescandataI>> scanTypeMap = new HashMap<String,TreeSet<XnatImagescandataI>>();
    private final Map<String,TreeSet<XnatImagescandataI>> usableScanTypeMap = new HashMap<String,TreeSet<XnatImagescandataI>>();
	private static enum TASK_TYPES { WM,MOTOR,SENT,STORY };
	
	static final String EPRIME_TXT_EXT = "txt";
	static final String EPRIME_EDAT_EXT = "edat2";
	static final String[] EEG_EXTS = { ".el","hs","el.ascii" };
	private static final Pattern EPRIME_DP = Pattern.compile("_\\d\\d-\\d\\d-\\d\\d\\d\\d_\\d\\d.\\d\\d.\\d\\d"); 
	private static final DateFormat EPRIME_DF = new SimpleDateFormat("MM-dd-yyyy_HH.mm.ss");
	private static final String NO_EPRIME_STR = "Uploader found no e-prime files for this scan";

	List<File> cacheFiles = null;
	
	// E-prime file recieved thus far have been 16-bit 
	static final String DEFAULT_ENCODING = "UTF-16LE";
	static final String EVENT_REASON = "Upload MEG Scan-Linked Files";

	static Logger logger = Logger.getLogger(HCPMEGLinkedDataImporter.class);
	
	private final FileWriterWrapperI fw;
	private final UserI user;
	final Map<String,Object> params;
		private XnatProjectdata proj;
		private XnatMegsessiondata exp;
	private List<String> returnList = new ArrayList<String>();;
	// Per e-mail from Abbas, only series descriptions with "_B" should have EEG files attached
	private static final String EEG_INCLUDE = "_B";
	 
	private static final Comparator<File> eprimeFileCompare = new Comparator<File>() {
		@Override
		public int compare(File arg0, File arg1) {
			final Date d0 = getDateFromEprimeFile(arg0);
			final Date d1 = getDateFromEprimeFile(arg1);
			if (d0 != null && d1 != null && !d0.equals(d1)) {
				return d0.compareTo(d1);
			} else {
				return arg0.getName().compareTo(arg1.getName());
			}
		}
	};

	private static final Comparator<XnatImagescandataI> scanCompare = new Comparator<XnatImagescandataI>() {
		@Override
		public int compare(XnatImagescandataI arg0, XnatImagescandataI arg1) {
			try {
				return Float.valueOf(arg0.getId()).compareTo(Float.valueOf(arg1.getId()));
			} catch (NumberFormatException e) {
				final String numPart0 = arg0.getId().replaceAll("\\D", "");
				final String numPart1 = arg1.getId().replaceAll("\\D", "");
				if (!numPart0.equals(numPart1)) {
					return numPart0.compareTo(numPart1);
				}
				return arg0.getId().compareTo(arg1.getId());
			}
		}
	};

	/**
	 * 
	 * @param listenerControl
	 * @param u
	 * @param session
	 * @param overwrite:   'append' means overwrite, but preserve un-modified content (don't delete anything)
	 *                      'delete' means delete the pre-existing content.
	 * @param additionalValues: should include project (subject and experiment are expected to be found in the archive)
	 */
	public HCPMEGLinkedDataImporter(Object listenerControl, UserI u, FileWriterWrapperI fw, Map<String, Object> params) {
		super(listenerControl, u, fw, params);
		this.user = u;
		this.fw = fw;
		this.params = params;
	}
	
	public HCPMEGLinkedDataImporter(UserI u, Map<String, Object> params) {
		// NOTE:  This constructor is primarily for use from automation calls.
		this(null, u, null, params);
	}

	@Override
	public List<String> call() throws ClientException, ServerException {
		if (this.fw == null) {
			return callFromAutomationUploader();
		}
		verifyAndGetExperiment();
		try {
			processUpload();
			this.completed("Success");
			return returnList;
		} catch (ClientException e) {
			logger.error("",e);
			this.failed(e.getMessage());
			throw e;
		} catch (ServerException e) {
			logger.error("",e);
			this.failed(e.getMessage());
			throw e;
		} catch (Throwable e) {
			logger.error("",e);
			throw new ServerException(e.getMessage(),new Exception());
		}
	}
	
	public List<String> callFromAutomationUploader() throws ClientException, ServerException {
		if (!this.params.containsKey("BuildPath")) {
			throw new ClientException("BuildPath was not specified");
		}
		verifyAndGetExperiment();
		try {
			processCacheFiles(new File(this.params.get("BuildPath").toString()));
			return returnList;
		} catch (ClientException e) {
			logger.error("",e);
			throw e;
		} catch (ServerException e) {
			logger.error("",e);
			throw e;
		} catch (Throwable e) {
			logger.error("",e);
			throw new ServerException(e.getMessage(),new Exception());
		}
	}
	
	private void verifyAndGetExperiment() throws ClientException {
		String projID = null;
		if (params.get("project") != null) {
			projID = params.get("project").toString();
		}
		String subjLbl = null;
		if (params.get("subject") != null) {
			subjLbl = params.get("subject").toString();
		}
		if (params.get("experiment") == null) {
			clientFailed("ERROR:  experiment parameter (containing experiment label) must be supplied for import");
		}
		String expLbl = params.get("experiment").toString();
		
		this.exp = XnatMegsessiondata.getXnatMegsessiondatasById(expLbl, user, true);
		if(exp == null){
			ArrayList<XnatMegsessiondata> al = XnatMegsessiondata.getXnatMegsessiondatasByField("xnat:mrSessionData/label",expLbl, user, true);
			// Using iterator here because removing within for/next can be unpredictable
			Iterator<XnatMegsessiondata> aIter = al.iterator();
			while (aIter.hasNext()) {
				XnatMegsessiondata mrsess = aIter.next();
				if (projID != null && !mrsess.getProject().equalsIgnoreCase(projID)) {
					aIter.remove();
					continue;
				}
				if (subjLbl != null && 
						!(mrsess.getSubjectData().getId().equalsIgnoreCase(subjLbl) || mrsess.getSubjectData().getLabel().equalsIgnoreCase(subjLbl))) {
					aIter.remove();
					continue;
				}
				exp = mrsess;
			}
			if (al.size()>1) {
				clientFailed("ERROR:  Multiple sessions match passed parameters. Consider using experiment assession number or supplying project/subject parameters ");
			}
		}
			if (exp == null) {
			clientFailed("ERROR:  Could not find matching experiment for import");
		}
			proj = exp.getProjectData();
	}
	
	private void clientFailed(final String fmsg) throws ClientException {
		this.failed(fmsg);
		throw new ClientException(fmsg,new Exception());
	}

	private void processUpload() throws ClientException,ServerException {
		
		String cachePath = ArcSpecManager.GetInstance().getGlobalCachePath();
		
		boolean doProcess = false;
		// Multiple uploads are allowed to same space (processing will take place when process parameter=true).  Use specified build path when
		// one is given, otherwise create new one
		String specPath = null;
		boolean invalidSpecpath = false;
		if (params.get("buildPath") != null) {
			// If buildpath parameter is specified and valid, use it
			specPath = params.get("buildPath").toString();
			if (specPath.indexOf(cachePath)>=0 && specPath.indexOf("user_uploads")>=0 &&
					specPath.indexOf(File.separator + user.getID() + File.separator)>=0 && new File(specPath).isDirectory()) {
				cachePath = specPath;
			} else {
				specPath = null;
				invalidSpecpath = true;
			}
		} 
		if (specPath == null) {
			final Date d = Calendar.getInstance().getTime();
			final java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat ("yyyyMMdd_HHmmss");
			final String uploadID = formatter.format(d);
			// Save input files to cache space
			cachePath+="user_uploads/"+user.getID() + "/" + uploadID + "/";
		}
		// If uploading zip file with no process or build directory specified, will proceed with processing regardless unless told not 
		// to via process parameter.  Otherwise will only process when told to (when done uploading).
		String processParm = null;
		if (params.get("process") != null) {
			processParm = params.get("process").toString();
		}
		
		final File cacheLoc = new File(cachePath);
		cacheLoc.mkdirs();
		
		// If uploading a file, process it.  Otherwise just set doProcess parameter (default=true)
		if (fw != null) {
			doProcess = processFile(cacheLoc, specPath, processParm, doProcess, invalidSpecpath);
		} else {
			if (!(processParm != null && (processParm.equalsIgnoreCase("NO") || processParm.equalsIgnoreCase("FALSE")))) {
				doProcess = true;
			}
		}
		
		// Conditionally process cache location files, otherwise return cache location
		if (doProcess) {
			processCacheFiles(cacheLoc);
		} else {
			returnList.add(cachePath);
		}
		
	}

	private boolean processFile(final File cacheLoc,final  String specPath,final  String processParm,boolean doProcess,final  boolean invalidSpecpath) throws ClientException {
		final String fileName = fw.getName();
		if (specPath == null && (isZipFileName(fileName) || isTarFileName(fileName))) {
			if (!(processParm != null && (processParm.equalsIgnoreCase("NO") || processParm.equalsIgnoreCase("FALSE")))) {
				doProcess = true;
			}
		// If not uploading a zip file, only process when told to (via processParm) 
		} else if (!isZipFileName(fileName) && !isTarFileName(fileName)) {
			if (processParm != null && (processParm.equalsIgnoreCase("YES") || processParm.equalsIgnoreCase("TRUE"))) {
				doProcess = true;
			}
		}
		
		if (invalidSpecpath && !doProcess) {
			throw new ClientException("ERROR:  Specified build path is invalid");
		}
		
		if (isZipFileName(fileName)) {
			final ZipI zipper = getZipper(fileName);
			try {
				zipper.extract(fw.getInputStream(),cacheLoc.getAbsolutePath());
			} catch (Exception e) {
				throw new ClientException("Archive file is corrupt or not a valid archive file type.");
			}
		} else if (isTarFileName(fileName)) {
			try {
				final ZipI zipper = getZipper(fileName);
	    		// TAR files didn't work extracted by stream
				final File cacheFile = new File(cacheLoc,fileName);
				final FileOutputStream fout = new FileOutputStream(cacheFile);
				// Binary Copy
				final InputStream fin = new BufferedInputStream(fw.getInputStream());
				int noOfBytes = 0;
				byte[] b = new byte[8*1024];
				while ((noOfBytes = fin.read(b))!=-1) {
					fout.write(b,0,noOfBytes);
				}
				fin.close();
				fout.close();
	    		zipper.extract(cacheFile,cacheLoc.getAbsolutePath(),false);
	    		cacheFile.delete();
			} catch (Exception e) {
				throw new ClientException("Archive file is corrupt or not a valid archive file type.");
			}
		} else {
			File cacheFile = new File(cacheLoc,fileName);
			try {
				final FileOutputStream fout = new FileOutputStream(cacheFile);
				final OutputStreamWriter writer; 
				if (fileName.toLowerCase().endsWith("." + EPRIME_EDAT_EXT) || fileName.toLowerCase().endsWith(".xlsx")) {
					// Binary Copy
					final InputStream fin = fw.getInputStream();
					int noOfBytes = 0;
					byte[] b = new byte[1024];
					while ((noOfBytes = fin.read(b))!=-1) {
						fout.write(b,0,noOfBytes);
					}
					fin.close();
					fout.close();
				} else {
					// Preserve encoding
					writer = new OutputStreamWriter(fout,DEFAULT_ENCODING);
					IOUtils.copy(fw.getInputStream(), writer, DEFAULT_ENCODING);
					writer.close();
				}
			} catch (IOException e) {
				throw new ClientException("Could not write uploaded file.");
			}
		}
		return doProcess;
		
	}
	
	private boolean isTarFileName(final String fileName) {
		for (final String ext : TAR_EXT) {
			if (fileName.toLowerCase().endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	private boolean isZipFileName(final String fileName) {
		for (final String ext : ZIP_EXT) {
			if (fileName.toLowerCase().endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	private void processCacheFiles(final File cacheLoc) throws ClientException, ServerException {
		
		returnList.add("<b>BEGIN PROCESSING UPLOADED FILES (EXPERIMENT = " + exp.getLabel() + ")</b>");
		final TreeSet<XnatImagescandataI> scanSet = new TreeSet<XnatImagescandataI>(scanCompare);
		scanSet.addAll(exp.getScans_scan());
		processEEGFiles(cacheLoc,scanSet);
		processEprimeFiles(cacheLoc,scanSet);
		
		returnList.add("<br><b>REPORT ON ANY LEFTOVER FILES</b>");
		
		// Currently physio, head-tracker files
		reportLeftOvers(cacheLoc);
		
		refreshFileCountsInDB();

		
		
		
		
	}
	
	private void refreshFileCountsInDB() {
		
		List<XnatImagescandata> scans = exp.getScansByXSIType("xnat:megScanData");
		
		final String projectPath = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc();
		// Refresh scan-level resources
		for (final XnatImagescandata scan : scans) {
			for (final XnatAbstractresourceI rs : scan.getFile()) {
				if (!rs.getLabel().equals(LINKED_RESOURCE_LABEL)) {
					continue;
				}
				refreshCounts(scan.getItem(),rs,projectPath);
			}
		}
		// Refresh session-level resource
		for (final XnatAbstractresourceI rs : exp.getResources_resource()) {
			if (!rs.getLabel().equals(LINKED_RESOURCE_LABEL)) {
				continue;
			}
			refreshCounts(exp.getItem(),rs,projectPath);
		}
	}
	
	public void refreshCounts(XFTItem it,XnatAbstractresourceI resource,String projectPath) {
		try {
			// Clear current file counts and sizes so they will be recomputed instead of pulled from existing values
			if (resource instanceof XnatResourcecatalog) {
				((BaseXnatResourcecatalog)resource).clearCountAndSize();
				((XnatResourcecatalog)resource).clearFiles();
			}
			final CatalogService _catService = XDAT.getContextService().getBean(CatalogService.class);
			_catService.refreshResourceCatalog(user, ((XnatResourcecatalog)resource).getUri(), CatalogService.Operation.ALL);			
			
		} catch (Exception e) {
			returnList.add("WARNING:  Could not update file counts for item");
		}
	}

	private void processEEGFiles(File cacheLoc, TreeSet<XnatImagescandataI> scanSet) throws ClientException,ServerException {
		final List<File> eegFiles = getEEGFiles(getCacheFileList(cacheLoc));
		for (final XnatImagescandataI scan : scanSet) {
			if (scan.getSeriesDescription().contains(EEG_INCLUDE)) {
				addEEGFilesToScan(scan,eegFiles);
			}
		}
		// EEG Files are uploaded to multiple scans, so we work with copies of the files.  Here we remove the originals, so
		// they are not treated as leftovers.
		for (final File f : eegFiles) {
			f.delete();
		}
	}

	private void processEprimeFiles(File cacheLoc, TreeSet<XnatImagescandataI> scanSet) throws ClientException,ServerException {
		final Map<File,File> eprimeMap = getEprimeFileMap(getCacheFileList(cacheLoc));
		for (final XnatImagescandataI scan : scanSet) {
			populateScanTypeMap(scan);
		}
		
		// Add E-Prime files to scans
		if (checkEprimeMatches()) {
			
			for (final String stype : scanTypeMap.keySet()) {
				
				TreeSet<XnatImagescandataI> sset = scanTypeMap.get(stype);
				final TreeSet<File> fset = eprimeTypeMap.get(stype);
				if (fset == null) {
					// NO E-PRIME FILES UPLOADED
					for (final XnatImagescandataI scan : sset) {
						addNoteToScan(scan);
					}						
					continue;
				}
				if (sset.size()!=fset.size()) {
					sset = usableScanTypeMap.get(stype);
				}
				final Iterator<File> fiter = fset.iterator();
				for (final XnatImagescandataI scan : sset) {
					final File edatf = fiter.next();
					final File textf = eprimeMap.get(edatf);
					// pull run number from edat file
					//final String run_number = edatf.getName().substring(0,edatf.getName().indexOf(".edat2")).replaceAll("^.*[^0-9]","");
					if (textf == null) {
						returnList.add("WARNING:  Could not find matching txt file for edat file " + edatf.getName() +
										".  E-prime must be uploaded manually for scan.");
						addNoteToScan(scan);
						continue;
					}
					addEprimeFilesToScan(scan,edatf,textf);
				}
				
			}
		
		}
		
	}
	
	private void addNoteToScan(XnatImagescandataI scan) {
		if (scan.getSeriesDescription()==null || scan.getSeriesDescription().matches("^.*[0-9]$") ||
				(scan.getNote() != null && scan.getNote().contains(NO_EPRIME_STR))) {
			return;
		}
		final String currNote = scan.getNote();
		scan.setNote((currNote==null || currNote.length()<1)  ? NO_EPRIME_STR : currNote + ", " + NO_EPRIME_STR);
		try {
			PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(),
					EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, EventUtils.MODIFY_VIA_WEB_SERVICE, null, null));
			final EventMetaI ci = wrk.buildEvent();
			if (SaveItemHelper.authorizedSave(exp,user,false,false,ci)) {
				PersistentWorkflowUtils.complete(wrk, ci);
			} else {
				PersistentWorkflowUtils.fail(wrk,ci);
			}
		} catch (Exception e) {
			// Do nothing, note will not be added
			e.printStackTrace();
		}
	}

	private boolean checkEprimeMatches() {
			
		boolean hasEprime = false;
		for (final String stype : scanTypeMap.keySet()) {
			TreeSet<XnatImagescandataI> sset = scanTypeMap.get(stype);
			final TreeSet<File> fset = eprimeTypeMap.get(stype);
			if (fset == null) {
				// NO E-PRIME FILES UPLOADED FOR THIS TYPE
				continue;
			}
			if (sset.size() != fset.size()) {
				returnList.add("WARNING:  Number of eprime files for " + stype + " (" + fset.size() + ") does not match the number of scans (" +
								sset.size() + ").  Will try matching to usable scans.");
				sset = usableScanTypeMap.get(stype);
				if (sset.size() != fset.size()) {
					returnList.add("ERROR:  Number of eprime files for " + stype + " (" + fset.size() + ") does not match the number usable or unusable scans (" +
									sset.size() + ").  E-PRIME files will not be uploaded.");
					return false;
				}
			}
			hasEprime = true;
		}
		return hasEprime;
			
	}

	private Map<File, File> getEprimeFileMap(List<File> files) {
		final Iterator<File> zipI = files.iterator();
		final Map<File,File> returnMap = new HashMap<File,File>();
		while (zipI.hasNext()) {
			final File zipFile = zipI.next();
			if (zipFile.getName().endsWith(".edat2") && !
					(zipFile.getName().toLowerCase().contains("practice") || zipFile.getName().toLowerCase().contains("runp"))) {
				// Find and place matching txt file
				final String tPath = zipFile.getAbsolutePath().substring(0,zipFile.getAbsolutePath().lastIndexOf(".edat2")) + ".txt";
				final File tFile = new File(tPath);
				if (files.contains(tFile)) {
					final File epFile = renameEprimeFile(zipFile);
					final File tpFile = renameEprimeFile(tFile);
					returnMap.put(epFile,tpFile);
					populateEprimeTypeMap(epFile);
				}
			} else if (zipFile.getName().endsWith(".edat2")) {
				returnList.add("NOTE:  e-prime file " + zipFile.getName() + " and its associated txt file appear to be practice files.  They will not be uploaded.");
				zipI.remove();
			}
		}
		return returnMap;
	}

	private File renameEprimeFile(File f) {
		String newName = f.getName().replaceFirst("__.*[.]",".");
		final String[] parts = newName.split("_");
		if (parts.length>=2) {
			if (parts[1].toUpperCase().startsWith("MEG")) {
				newName = newName.replaceFirst("^[^_]*_[^_]*_", exp.getLabel() + "_");
			} else {
				newName = newName.replaceFirst("^[^_]*_", exp.getLabel() + "_");
			}
		}
		if (!newName.equals(f.getName())) {
			final File newFile = new File(f.getParentFile(),newName);
			if (f.renameTo(newFile)) {
				returnList.add("NOTE:  EPRIME file " + f.getName() + " was renamed to " + newFile.getName());
				return newFile;
			}
		}
		return f;
	}
	

	private void populateEprimeTypeMap(File zipFile) {
		String ftype;
		final String fn = zipFile.getName();
		if (fn.toLowerCase().contains("_motor")) {
			ftype = TASK_TYPES.MOTOR.toString(); 
		} else if (fn.toLowerCase().contains("_sent")) {
			ftype = TASK_TYPES.SENT.toString(); 
		} else if (fn.toLowerCase().contains("_story")) {
			ftype = TASK_TYPES.STORY.toString(); 
		} else if (fn.toLowerCase().contains("_wm") || fn.toLowerCase().contains("_wrkmem")) {
			ftype = TASK_TYPES.WM.toString(); 
		} else {
			return;
		}
		if (!eprimeTypeMap.containsKey(ftype)) {
			final TreeSet<File> fset = new TreeSet<File>(eprimeFileCompare);
			fset.add(zipFile);
			eprimeTypeMap.put(ftype,fset);
		} else {
			eprimeTypeMap.get(ftype).add(zipFile);			
		}
	}

	private void populateScanTypeMap(XnatImagescandataI scan) {
		String stype;
		if (scan.getSeriesDescription().contains("Motor")) {
			stype = TASK_TYPES.MOTOR.toString(); 
		} else if (scan.getSeriesDescription().contains("Sentnc")) {
			stype = TASK_TYPES.SENT.toString(); 
		} else if (scan.getSeriesDescription().contains("Story")) {
			stype = TASK_TYPES.STORY.toString(); 
		} else if (scan.getSeriesDescription().contains("Wrkmem")) {
			stype = TASK_TYPES.WM.toString(); 
		} else {
			return;
		}
		if (!scanTypeMap.containsKey(stype)) {
			final TreeSet<XnatImagescandataI> sset = new TreeSet<XnatImagescandataI>(scanCompare);
			sset.add(scan);
			scanTypeMap.put(stype,sset);
		} else {
			scanTypeMap.get(stype).add(scan);			
		}
	}
	
	private void addEEGFilesToScan(XnatImagescandataI scan, List<File> eegFiles) throws ClientException,ServerException {
		if (eegFiles == null || eegFiles.size()==0) {
			return;
		}
		for (final File f : eegFiles) {
			addFileToScan(scan,f,resourceMapEEG,FileType.EEG);
		}
	}
	
	private void addEprimeFilesToScan(XnatImagescandataI scan, File edatf, File textf) throws ClientException,ServerException {
		if (edatf == null || textf == null) {
			return;
		}
		addFileToScan(scan,edatf,resourceMapLinked,FileType.EPRIME);
		addFileToScan(scan,textf,resourceMapLinked,FileType.EPRIME);
	}
	
	private void addFileToScan(XnatImagescandataI thisScan, File file, Map<ResourceInfo, String> resourceMap, FileType fileType) throws ClientException, ServerException {
		
		final String fileCatPath = (fileType != FileType.EEG) ? fileType.toString() + File.separator + file.getName() : file.getName();
		final String type = fileType.toString();
		// For EEG, we need to copy the same file to multiple scans, so we'll create a temporary copy.
		File addFile;
		try {
			addFile = (fileType == FileType.EEG) ? File.createTempFile("addFile", "tmp") : file;
			if (fileType == FileType.EEG) {
				FileUtils.copyFile(file, addFile);
			}
		} catch (IOException e1) {
			throw new ServerException("ERROR:  Could not copy EEG file for adding to scan (FILE=" + file.getName() + ")");
		}
		
		try {
			
			// See if file already exists
			boolean alreadyExists = doesExist(thisScan,file,type,fileCatPath,resourceMap);
			
			String eventStr;
			if (alreadyExists) {
				// Will replace it
				eventStr = "Replaced existing " +  type + " File (<b>" + file.getName() + "</b> for scan " + thisScan.getId() + " (SD=<b>" + thisScan.getSeriesDescription() + "</b>))" ;
			} else {
				// Will add it
				eventStr = "Uploaded " +  type + " File (<b>" + file.getName() + "</b> for scan " + thisScan.getId() + " (SD=<b>" + thisScan.getSeriesDescription() + "</b>))" ;
			}
			
			final PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(),
					EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, EventUtils.ADDED_MISC_FILES, eventStr, null));
			final EventMetaI ci = wrk.buildEvent();
			
			final DirectScanResourceImpl scanModifier = getScanModifier(thisScan,ci);
			createResourceIfNecessary(thisScan,scanModifier,resourceMap);
			
			// Add both text and e-prime file
			final ArrayList<StoredFile> fws = new ArrayList<StoredFile>();
			fws.add(new StoredFile(addFile,alreadyExists));
			boolean rc = true;
			/*
			try {
				List<String> duplicates = scanModifier.addFile(fws, resourceMap.get(ResourceInfo.RESOURCE_LABEL), null,fileCatPath, new XnatResourceInfo(user,new Date(),new Date()), false);
			} catch (Exception e) {
				rc = false;
			}
			*/
			if (rc) {
				
				PersistentWorkflowUtils.complete(wrk, ci);
				returnList.add(eventStr); 
				
			} else {
				
				PersistentWorkflowUtils.fail(wrk, ci);
				returnList.add("WARNING:  Could not add " +  type + " File (<b>" + file.getName() + "</b> to scan " + thisScan.getId() + " (SD=<b>" + thisScan.getSeriesDescription() + "</b>))");
			
			}
			
		} catch (Exception e) {
			throw new ClientException("ERROR:  Could not add " + type + " files to resource");
		}

	}

	private boolean doesExist(XnatImagescandataI thisScan, File file, String type, String fileCatPath, Map<ResourceInfo, String> resourceMap) {
		List<XnatAbstractresourceI> thisFL = thisScan.getFile();
		return checkFileExist(thisFL,file,type,fileCatPath,resourceMap);
	}
	
	@SuppressWarnings("unchecked")
	private boolean checkFileExist(List<XnatAbstractresourceI> thisFL, File pFile,
			String type, String fileCatPath, Map<ResourceInfo, String> resourceMap) {
		for (final XnatAbstractresourceI thisF : thisFL) {
			if (thisF instanceof XnatResourcecatalog) {
				if (thisF.getLabel().equals(resourceMap.get(ResourceInfo.RESOURCE_LABEL))) {
					final XnatResourcecatalog thisC = (XnatResourcecatalog) thisF;
					final ArrayList<File> files = thisC.getCorrespondingFiles("/");
					for (final File f : files) {
						if (f.getPath().endsWith(fileCatPath)) {
							return true;
						}
					}
					break;
				}
			}
		}
		return false;
	}

	private void createResourceIfNecessary(XnatImagescandataI thisScan, DirectScanResourceImpl scanModifier, Map<ResourceInfo, String> resourceMap) throws ClientException {

		final XnatAbstractresourceI resource =  scanModifier.getResourceByLabel(resourceMap.get(ResourceInfo.RESOURCE_LABEL), null);
		if (resource == null) {
			createResource((AutoXnatImagescandata)thisScan,resourceMap);
		}
		
	}
	
	private void createResource(AutoXnatImagescandata thisScan,Map<ResourceInfo, String> resourceMap) throws ClientException {
		
		final File scanDir = new File(ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc() + File.separator + exp.getLabel() +
					File.separator + "SCANS" + File.separator + thisScan.getId() + File.separator + resourceMap.get(ResourceInfo.RESOURCE_LABEL));
		if (!scanDir.exists()) {
			scanDir.mkdirs();
		}
		final File catFile = new File(scanDir,resourceMap.get(ResourceInfo.CATXML_PREFIX) + thisScan.getId() + resourceMap.get(ResourceInfo.CATXML_EXT));
		final CatCatalog cat = new CatCatalog();
		final XnatResourcecatalog ecat = new XnatResourcecatalog();
		try {
			final FileWriter fw = new FileWriter(catFile);
			cat.toXML(fw);
			fw.close();
			// Set URI to archive path
			ecat.setUri(catFile.getAbsolutePath());
		} catch (IOException e) {
			throw new ClientException("Couldn't write catalog XML file",e);
		} catch (Exception e) {
			throw new ClientException("Couldn't write catalog XML file",e);
		}
		ecat.setLabel(resourceMap.get(ResourceInfo.RESOURCE_LABEL));
		ecat.setFormat(resourceMap.get(ResourceInfo.RESOURCE_FORMAT));
		ecat.setContent(resourceMap.get(ResourceInfo.RESOURCE_CONTENT));
		// Save resource to scan
		final String eventStr = "Resource " + resourceMap.get(ResourceInfo.RESOURCE_LABEL) + " created under scan " + thisScan.getId() + " (SD=" + thisScan.getSeriesDescription() + ")" ;
		try {
			thisScan.addFile(ecat);
			
			final PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, exp.getItem(),
					EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, EventUtils.CREATE_RESOURCE, eventStr, null));
			final EventMetaI ci = wrk.buildEvent();
			if (SaveItemHelper.authorizedSave(thisScan,user,false,true,ci)) {
				PersistentWorkflowUtils.complete(wrk, ci);
			} else {
				PersistentWorkflowUtils.fail(wrk,ci);
			}
			returnList.add(eventStr); 
		} catch (Exception e) {
			throw new ClientException("ERROR:  Couldn't add resource to scan - " + e.getMessage(),new Exception());
		}
		
	}

	
	private DirectScanResourceImpl getScanModifier(final XnatImagescandataI scan,final EventMetaI ci) {
		if (scanModifiers.containsKey(scan.getId())) {
			return scanModifiers.get(scan.getId());
		} else {
			final DirectScanResourceImpl scanModifier = new DirectScanResourceImpl((XnatImagescandata)scan,exp,true,user,ci);
			scanModifiers.put(scan.getId(),scanModifier);
			return scanModifier;
		}
	}
	
	private List<File> getCacheFileList(final File dir) {
		if (cacheFiles == null) {
			cacheFiles = getFileListFromCache(dir);
		}
		return cacheFiles;
	}
	

	private List<File> getFileListFromCache(final File dir) {
		final List<File> files = new ArrayList<File>();
		if (!dir.isDirectory()) {
			return files;
		}
		for (final File f : dir.listFiles()) {
			if (f.isDirectory()) {
				files.addAll(getFileListFromCache(f));
			} else {
				files.add(f);
			}
		}
		return files;
	}

	private List<File> getEEGFiles(final List<File> infiles) {
		final List<File> files = new ArrayList<File>();
		// Per Abbas, 2013/04/15, currently not appending EEG files.  Leaving code in place in case decision is reversed or revised
		/*
		final List<String> hasExt = new ArrayList<String>();
		for (final File f : infiles) {
			for (final String ext : Arrays.asList(EEG_EXTS)) {
				if (f.getName().endsWith(ext)) {
					if (!f.getName().toLowerCase().startsWith(exp.getSubjectData().getLabel().toLowerCase())) {
						returnList.add("WARNING:  EEG File name does not match expectation (BEGINS WITH SUBJECT LABEL -- (SUBJECT_LBL = " +
								exp.getSubjectData().getLabel() + ",FILE_NAME=" + f.getName() + ")).  Please verify the data is for the correct subject."); 
					}
					if (hasExt.contains(ext)) {
						returnList.add("WARNING:  More than one EEG file with extension " + ext + ".  This is likely a problem.");
					}
					files.add(f);
				}
			}
		}
		if (!(files.size() == EEG_EXTS.length) && files.size()>0) {
			returnList.add("WARNING:  Unexpected number of EEG files (EXPECTED=" + EEG_EXTS.length + ", FOUND=" + files.size() + ").");
		}
		*/
		return files;
	}

	///////////////////////////
	// LEFTOVER FILE METHODS //
	///////////////////////////
	
	private void reportLeftOvers(File cacheLoc) {
		if (!cacheLoc.isDirectory()) {
			return;
		}
		final File[] fList = cacheLoc.listFiles();
		Arrays.sort(fList, new Comparator<File>() {
				public int compare(final File o1,final File o2) {
					return (o1.getName().compareToIgnoreCase(o2.getName()));
				}});
		for (final File f : fList) {
			if (f.isDirectory()) {
				reportLeftOvers(f);
				
			} else {
				returnList.add("NOTE:  Uploaded file - <b>" + f.getName() + "</b> - was not uploaded to any scan"); 
			}
		}
	}

	private ZipI getZipper(String fileName) {
		
		// Assume file name represents correct compression method
        String file_extension = null;
        if (fileName != null && fileName.indexOf(".")!=-1) {
        	file_extension = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
        	if (Arrays.asList(ZIP_EXT).contains(file_extension)) {
        		return new ZipUtils();
	        } else if (file_extension.equalsIgnoreCase(".tar")) {
        		return new TarUtils();
	        } else if (file_extension.equalsIgnoreCase(".gz")) {
	        	TarUtils zipper = new TarUtils();
	        	zipper.setCompressionMethod(ZipOutputStream.DEFLATED);
	        	return zipper;
	        }
        }
        // Assume zip-compression for unnamed inbody files
        return new ZipUtils();
        
	}

	private static Date getDateFromEprimeFile(File zipFile) {
		final Matcher matcher = EPRIME_DP.matcher(zipFile.getName());
		if (matcher.find()) {
			try {
				return EPRIME_DF.parse(matcher.group().substring(1));
			} catch (ParseException e1) { }
		}
		return null;
	}

}

