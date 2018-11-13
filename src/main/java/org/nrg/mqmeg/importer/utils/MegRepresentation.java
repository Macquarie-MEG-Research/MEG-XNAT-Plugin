package org.nrg.mqmeg.importer.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
//import java.lang.ProcessBuilder;      // used to do python logging
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.io.EndianUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.attr.Utils;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.base.BaseElement;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.bean.CatEntryBean;
import org.nrg.xdat.bean.XnatMegscandataBean;
import org.nrg.xdat.bean.XnatMegsessiondataBean;
import org.nrg.xdat.bean.XnatResourcecatalogBean;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagescandata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.om.XnatMegsessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.om.base.BaseXnatResourcecatalog;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventMetaI;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xft.utils.SaveItemHelper;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.services.archive.CatalogService;

public class MegRepresentation {
	
	static Logger logger = Logger.getLogger(MegRepresentation.class);

	private String subjectLbl;
	private String expLbl;
	private File megDirectory; 
    private UserI user;
    private XnatSubjectdata subject;
	private String cachepath;
    private XnatProjectdata proj;
	private List<String> returnList = new ArrayList<String>();
	
	private ArrayList<MEGExperiment> scanList; 
	
	private static final DateFormat SDA_DF = new SimpleDateFormat("MM/dd/yy HH:mm"); 
	
	/*
	 * Create MEG Representation from newer format (no tocf file).  Builds archive session from the representation.
	 * @author Mike Hodge <hodgem@mir.wustl.edu>
	 */
	public MegRepresentation(UserI user, XnatProjectdata proj, XnatSubjectdata subject, final String cachepath) throws ClientException {
		final ArrayList<File> fileList = getFileListFromDir(new File(cachepath));

		this.proj = proj;
        this.user = user;
        this.subject = subject;
		this.subjectLbl = subject.getLabel();
		this.expLbl = newExpLbl();
		this.cachepath = cachepath;

		// construct a scan list:
		this.scanList = new ArrayList<MEGExperiment>();
		final MEGExperiment megScan = new MEGExperiment();
		megScan.setExpID(proj.getId());
		megScan.setExpSubj(subjectLbl);
		megScan.setSessionList(new ArrayList<MEGSession>());
		scanList.add(megScan);
		// add a new session:
		final MEGSession megSess = new MEGSession();
		megSess.setSessionID(expLbl);
		megSess.setFileList(new ArrayList<MEGFile>());
		// add the session to the scan
		megScan.getSessionList().add(megSess);
		// create a file object for each file and add to the session
		for (File f : fileList)
		{
			try
			{
				URI cache_uri = new URI(cachepath);
				String rel_path = cache_uri.relativize(new URI(f.getPath())).toString();
				String fpath = f.getCanonicalPath();
				String ftype = getFileExtension(f);
				final MEGFile megFile = new MEGFile();
				megFile.setFileType(ftype);
				megFile.setFileIn(fpath);
				megFile.setFileOut(rel_path);
				returnList.add("Moving file " + f.getName() + " to the archive");
				megSess.getFileList().add(megFile);
			}
			catch (IOException e){}
			catch (URISyntaxException e){}
		}
	}

	private static String getFileExtension(File file)
	{
        String fileName = file.getName();
        if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
        return fileName.substring(fileName.lastIndexOf(".")+1);
		else return "";
	}

	private String newExpLbl() {
		final String lbl = this.subjectLbl;
       	StringBuilder tryLbl = new StringBuilder(lbl);
        ArrayList<XnatMegsessiondata> al=XnatMegsessiondata.getXnatMegsessiondatasByField("xnat:megSessionData/label",lbl, user, false);
        int i=2;
        while (al.size()>0) {
			tryLbl = new StringBuilder(lbl);
			tryLbl.append("_");
        	tryLbl.append(i);
        	al=XnatMegsessiondata.getXnatMegsessiondatasByField("xnat:megSessionData/label",tryLbl.toString(), user, false);
        	i++;
        }
        /*
		try {
			Process process = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", Integer.toString(i-1), "-sid", "newExpLbl").start();
		}
		catch (IOException e){};*/
        return tryLbl.toString();
	}

	private ArrayList<File> getFileListFromDir(File source) {
		ArrayList<File> fileList = new ArrayList<File>();
		if (!source.isDirectory()) {
			fileList.add(source);
			return fileList;
		}
		for (final File f : source.listFiles()) {
			if (f.isFile()) {
				fileList.add(f);
			} else if (f.isDirectory()) {
				fileList.addAll(getFileListFromDir(f));
			}
		}
		return fileList;
	}

	public void setSubjectLbl(String subjectLbl) {
		this.subjectLbl = subjectLbl;
	}

	public String getSubjectLbl() {
		return subjectLbl;
	}

	public void setExpLbl(String expLbl) {
		this.expLbl = expLbl;
	}

	public String getExpLbl() {
		return expLbl;
	}

	public void setMegDirectory(File megDirectory) {
		this.megDirectory = megDirectory;
	}

	public File getMegDirectory() {
		return megDirectory;
	}
		
	public void setScanList(ArrayList<MEGExperiment> scanList) {
		this.scanList=scanList;
	}
	
	public ArrayList<MEGExperiment> getScanList() {
		return scanList;
	}
	
	public class MEGExperiment implements Comparable<MegRepresentation.MEGExperiment> {
		
		private String expID; 			// ID of the experiment
		private String expSubj;			// ID of the subject (just for now...)
		private ArrayList<MEGSession> sessionList; 
		
		public void setExpID(String expID) {
			this.expID=expID;
		}
		
		public String getExpID() {
			return expID;
		}
		
		public void setExpSubj(String expSubj) {
			this.expSubj=expSubj;
		}
		
		public String getExpSubj() {
			return expSubj;
		}
		
		public void setSessionList(ArrayList<MEGSession> sessionList) {
			this.sessionList=sessionList;
		}
		
		public ArrayList<MEGSession> getSessionList() {
			return sessionList;
		}
		
		@Override
		public int compareTo(MEGExperiment cepx) {
			if (this.getSessionList().get(0)!=null && cepx.getSessionList().get(0)!=null) {
				return 0;
				//return this.getFileList().get(0).getSessionDateAsDate().compareTo(cexp.getFileList().get(0).getSessionDateAsDate());
			} else {
				// Should never happen
				return 0;
			}
		}

	}

	public class MEGScan implements Comparable<MegRepresentation.MEGScan> {
		
		private String scanID; 
		private String scanLbl; 
		private ArrayList<MEGSession> sessionList; 
		
		public void setScanID(String scanID) {
			this.scanID=scanID;
		}
		
		public String getScanID() {
			return scanID;
		}
		
		public void setScanLbl(String scanLbl) {
			this.scanLbl=scanLbl;
		}
		
		public String getScanLbl() {
			return scanLbl;
		}
		
		public void setSessionList(ArrayList<MEGSession> sessionList) {
			this.sessionList=sessionList;
		}
		
		public ArrayList<MEGSession> getSessionList() {
			return sessionList;
		}
		
		@Override
		public int compareTo(MEGScan cscan) {
			if (this.getSessionList().get(0)!=null && cscan.getSessionList().get(0)!=null) {
				return this.getSessionList().get(0).getSessionDateAsDate().compareTo(cscan.getSessionList().get(0).getSessionDateAsDate());
			} else {
				// Should never happen
				return 0;
			}
		}

	}
	
	public class MEGSession implements Comparable<MegRepresentation.MEGSession>
	{
		private String sessionID; 
		private String sessionDateStr; 		// will be retreived automatically eventually
		private ArrayList<MEGFile> fileList; 
		
		public void setSessionID(String sessionID) {
			this.sessionID = sessionID;
		}
		
		public String getSessionID() {
			return sessionID;
		}
		
		public void setSessionDateStr(String sessionDateStr) {
			this.sessionDateStr=sessionDateStr;
		}
		
		public String getSessionDateStr() {
			return sessionDateStr;
		}
		
		public void setFileList(ArrayList<MEGFile> fileList) {
			this.fileList=fileList;
		}
		
		public ArrayList<MEGFile> getFileList() {
			return fileList;
		}
		
		public Date getSessionDateAsDate() {
			try {
				return SDA_DF.parse(getSessionDateStr());
			} catch (ParseException e1) {
				return null;
			}
		}
		
		@Override
		public int compareTo(MEGSession csess) {
			return this.getSessionDateAsDate().compareTo(csess.getSessionDateAsDate());
		}

	}

	public class MEGFile {
		
		private String fileType; 
		private String fileIn; 			// the actual location of the file.
		private String fileOut; 
		
		public void setFileType(String fileType) {
			this.fileType = fileType;
		}

		public String getFileType() {
			return fileType.toUpperCase();
		}

		public void setFileIn(String fileIn) {
			this.fileIn = fileIn;
		}

		public String getFileIn() {
			return fileIn;
		}

		public void setFileOut(String fileOut) {
			this.fileOut = fileOut;
		}

		public String getFileOut() {
			return fileOut;
		}

	}

	public static String dequote(String str) {
		final int len = str.length();
		if (len > 1 && str.charAt(0) == '\"' && str.charAt(len - 1) == '\"') {
			return str.substring(1, len - 1).replaceAll("\\\"", "\"");
		}
		return str;
	}
	
	// Main function called by MegImporter
	public List<String> buildMegArchiveSession(XnatProjectdata proj, UserI user) throws ClientException
	{	
		this.proj = proj;
		
		final XnatMegsessiondataBean session = new XnatMegsessiondataBean();
		
		final XnatSubjectdata subj=XnatSubjectdata.GetSubjectByProjectIdentifier(proj.getId(),subjectLbl, user, false);
		if (subj == null) {
			throw new ClientException("ERROR:  Could not build session, subject " + subjectLbl + " does not exist.",new Exception());
		}
		final XnatExperimentdata existing=XnatExperimentdata.GetExptByProjectIdentifier(proj.getId(), expLbl, user, false);
		if (existing != null) {
			throw new ClientException("ERROR:  Experiment specified in the MEG archive (" + expLbl + ") already exists.",new Exception());
		}
		
		returnList.add("Creating session (SUBJECT=" + subj.getId() + ",SESSION=" + expLbl);
		
		session.setSubjectId(subj.getId());
		session.setProject(proj.getId());
		session.setLabel(expLbl);		// was expLbl. Needed to change so path could be done correctly
		session.setModality("MEG");
		session.setAcquisitionSite("MQ");
		session.setScanner("MQ MEG");
		
		return buildMegArchiveSessionFromScanList(session, user);
	}
	
	public Date ReadConFileDate(File conFile) throws FileNotFoundException
	{
		// Read the con file and return the date
		long timeOffset = 0x410L;
		try
		{
			InputStream f = new FileInputStream(conFile);
			try
			{
				// jump to the start of the time offset:
				f.skip(timeOffset);
				// now read the time
				int val = EndianUtils.readSwappedInteger(f);
				f.close();
				long time = 1000L*val;
				Date date = new Date(time);
				return date;
			}
			catch (IOException e){}
		}
		catch (FileNotFoundException e)
		{
			throw new FileNotFoundException("Cannot find the specified file" + conFile.toString());
		}
		

		return null;
	}

	public void DeleteSessionXml(File path)
	{
		try
		{
			path.delete();
		}
		catch (SecurityException e){}
	}

	private void writeCatalogXML(CatCatalogBean cat, XnatResourcecatalogBean rcat, File runInfoFile, File dir) throws ClientException {
		try {
			// ensure the directory exists
			//Process processa = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", runInfoFile.toString(), "-sid", "writecatxml_a").start();
			runInfoFile.getParentFile().mkdirs();
			final FileWriter fw = new FileWriter(runInfoFile);
			cat.toXML(fw);
			fw.close();
			// Set URI to archive path
			final String path = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc() + File.separator + expLbl +
				File.separator + "SCANS" + File.separator + Utils.getRelativeURI(dir,runInfoFile);
			//Process processb = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", path.toString(), "-sid", "writecatxml_b").start();
			rcat.setUri(path);
			//rcat.setUri(runInfoFile.getCanonicalPath());
		} catch (IOException e) {
			throw new ClientException("Couldn't write catalog XML file",e);
		} catch (Exception e) {
			throw new ClientException("Couldn't write catalog XML file",e);
		}
	}

	private void addFileToCatalog(CatCatalogBean cat, File f, File dir, boolean moveFile) throws ClientException {
		addFileToCatalog(cat,f,dir,null,moveFile);
	}

	private void addFileToCatalog(CatCatalogBean cat, File f, File dir,String uriDir, boolean moveFile) throws ClientException {
		final File outFile = new File(dir,f.getName());
		try {
			if (moveFile) {
				FileUtils.moveFile(f,outFile);
			} else {
				FileUtils.copyFile(f,outFile);
			}
			final CatEntryBean catEntry = new CatEntryBean();
			catEntry.setName(outFile.getName());
			StringBuilder uriSB = new StringBuilder();
			if (uriDir!=null) {
				uriSB.append(uriDir);
				if (!uriDir.endsWith("/")) {
					uriSB.append("/");
				}
			}
			uriSB.append(outFile.getName());
			catEntry.setUri(uriSB.toString());
			cat.addEntries_entry(catEntry);
		} catch (IOException e) {
			throw new ClientException(e.getMessage(),new Exception());
		}
	}

	public List<String> buildMegArchiveSessionFromScanList(XnatMegsessiondataBean session, UserI user) throws ClientException
	{

		// construct the final path for all the data to go
		final String output_path = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc() + File.separator + expLbl +
				File.separator + "SCANS";
		final File destPath = new File(output_path);
		//returnList.add(output_path);
		
		for (final MEGExperiment mscan : this.getScanList())
		{
			for (final MEGSession msess : mscan.getSessionList())
			{
				final XnatMegscandataBean scan = new XnatMegscandataBean();
				scan.setId(msess.getSessionID());
				scan.setType("MEG");
				scan.setSeriesDescription("MQ MEG data");
				scan.setQuality("usable");

				final File catFile = new File(output_path + File.separator + expLbl + "_catalog.xml");
				CatCatalogBean cat = new CatCatalogBean();

				// now sort out the files:
				for (final MEGFile mfile : msess.getFileList())
				{
					File sourceFile = new File(mfile.getFileIn());
					if (!sourceFile.exists()) {
						throw new ClientException("ERROR:  Source file structure is invalid or not currently supported by uploader.",new Exception());
					}
					try
					{
						//Process process = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m",mfile.getFileIn().toString(), "-sid", "path_in").start();
						// move the file to the output directory
						if (StringUtils.containsIgnoreCase(mfile.getFileType(), "CON"))
						{
							// reads from each one...
							session.setDate(ReadConFileDate(sourceFile));
						}
						//FileUtils.moveFileToDirectory(sourceFile, destPath, true);
						final CatEntryBean catEntry = new CatEntryBean();
						catEntry.setName(sourceFile.getName());
						catEntry.setUri(mfile.getFileOut());			// TODO: make relative filepath
						catEntry.setFormat(mfile.getFileType());
						catEntry.setCreatedby(user.getUsername());
						//catEntry.setContent("RAW");
						cat.addEntries_entry(catEntry);
						
					} catch (IOException e) {
						throw new ClientException(e.getMessage(),new Exception());
					}
				}

				// now move all the files.
				for (File f : new File(this.cachepath).listFiles())
				{
					try
					{
						if (f.isDirectory())
						{
							FileUtils.moveDirectoryToDirectory(f, destPath, true);
						}
						else
						{
							FileUtils.moveFileToDirectory(f, destPath, true);
						}
					}
					catch (IOException e){}

				}

				final XnatResourcecatalogBean rcat = new XnatResourcecatalogBean();
				writeCatalogXML(cat, rcat, catFile, destPath);
				rcat.setLabel("KIT");
				rcat.setFormat("BINARY");
				rcat.setContent("RAW");
				scan.addFile(rcat);

				session.addScans_scan(scan);
			}
		}

       	// Output session XML
		final File destFile = new File(output_path, expLbl + ".xml");
		try
		{
			final FileWriter fw = new FileWriter(destFile);
			session.toXML(fw);
			fw.close();
			
		} catch (Exception e) {
            throw new ClientException("Couldn't write output XML file",e);
		}
			
		try {
			
			// Save Session XML
			final SAXReader reader = new SAXReader(user);
			final XFTItem item = reader.parse(destFile);
			final XnatMegsessiondata megdat = (XnatMegsessiondata)BaseElement.GetGeneratedItem(item);
			final String new_id = XnatExperimentdata.CreateNewID();
			//Process processb = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", new_id.toString(), "-sid", "new_id").start();
			megdat.setId(new_id);
			
			final PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, megdat.getItem(),
					EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, EventUtils.CREATE_VIA_WEB_SERVICE, null, null));
			final EventMetaI ci = wrk.buildEvent();

			//final String expectedPath=megdat.getExpectedSessionDir().getAbsolutePath().replace('\\', '/');
			//Process process2 = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", expectedPath.toString(), "-sid", "expected").start();		// ~/arc001/Session_X

			if (SaveItemHelper.authorizedSave(megdat,user,false,false,ci)) {
				PersistentWorkflowUtils.complete(wrk, ci);
				//final String p = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc();
				//returnList.add(p);
			} else {
				PersistentWorkflowUtils.fail(wrk,ci);
			}
			
			returnList.add(0,"/archive/experiments/" + megdat.getId());
			// also delete the Session_X.xml file:
			DeleteSessionXml(destFile);
			refreshFileCountsInDB(megdat);
			
			return returnList;
			
		} catch (Exception e) {
            throw new ClientException("Couldn't save session",e);
		}
	}
	
	/////////////////////////
	// MOTION FILE METHODS //
	/////////////////////////

	private void refreshFileCountsInDB(XnatImagesessiondata exp) throws ClientException {
		
		final String proj_URI = "/archive/experiments/" + exp.getId();
		// Refresh session-level resources
		for (final XnatAbstractresourceI rs : exp.getResources_resource()) {
			refreshCounts(exp.getItem(), rs, proj_URI);
		}
		// Refresh scan-level resources
		List<XnatImagescandata> scans = exp.getScansByXSIType("xnat:megScanData");
		for (final XnatImagescandata scan : scans) {
			for (final XnatAbstractresourceI rs : scan.getFile())
			{
				try
				{
					refreshCounts(scan.getItem(), rs, proj_URI);
				}
				catch (ClientException e)
				{
					throw new ClientException("Error",e);
				}
			}
		}
	}
	
	public void refreshCounts(XFTItem it,XnatAbstractresourceI resource, String URI) throws ClientException
	{
		// the path needs to be /data/archive/~ I think...
		try {
			// Clear current file counts and sizes so they will be recomputed instead of pulled from existing values
			if (resource instanceof XnatResourcecatalog) {
				((BaseXnatResourcecatalog)resource).clearCountAndSize();
				((XnatResourcecatalog)resource).clearFiles();
			}
			final CatalogService _catService = XDAT.getContextService().getBean(CatalogService.class);
			_catService.refreshResourceCatalog(user, URI, CatalogService.Operation.ALL);		// ((XnatResourcecatalog)resource).getUri()
			
		} catch (Exception e) {
			//throw new ClientException("Can't update file count",e);
			returnList.add("WARNING:  Could not update file counts for item");
		}
	}

}
