package org.nrg.hcp.importer.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.lang.ProcessBuilder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

public class HCPMegRepresentation {
	
	static Logger logger = Logger.getLogger(HCPMegRepresentation.class);

	private String patientID; 
	private String subjectLbl;
	private String expLbl;
	private File megDirectory; 
	private UserI user;
	private String cachepath;
    private XnatProjectdata proj;
    private final Map<String,TreeSet<XnatMegscandataBean>> scanTypeMap = new HashMap<String,TreeSet<XnatMegscandataBean>>();
    private final Map<String,TreeSet<XnatMegscandataBean>> usableScanTypeMap = new HashMap<String,TreeSet<XnatMegscandataBean>>();
    private Map<String,Integer> sdCountMap = null;
    private List<File> eegFiles;
	private List<String> returnList = new ArrayList<String>();
	
	private ArrayList<MEGExperiment> scanList; 
	private SortedMap<File,SortedMap<File,ArrayList<File>>> dirStructure;
	
	private static final String DEFAULT_EXT = "_MEG";
	private static final String[] NOISE_FILES = { "c,rfDC","config" };
	private static final String[] MEG_FILES = { "c,rfDC","hs_file","config","e,rfhp[0-9.]*Hz,COH","e,rfhp[0-9.]*Hz,COH1" };
    private static final Map<String,String> convertMap;
    //private static final Map<String,Map<String,Long[]>> fileSizeMap;
	//private static final DateFormat DDT_DF = new SimpleDateFormat("MM%dd%yy@HH_mm"); 
	private static final DateFormat DDT_DF = new SimpleDateFormat("MM%dd%yy%HH%mm"); 
	private static final String DDT_SUBST = "\\D"; 
	private static final DateFormat SDA_DF = new SimpleDateFormat("MM/dd/yy HH:mm"); 
	private static final DateFormat STD_DF = new SimpleDateFormat("MM%dd%yy"); 
	private static final Pattern EPRIME_DP = Pattern.compile("_\\d\\d-\\d\\d-\\d\\d\\d\\d_\\d\\d.\\d\\d.\\d\\d"); 
	private static final DateFormat EPRIME_DF = new SimpleDateFormat("MM-dd-yyyy_HH.mm.ss");
	private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
	private static enum TASK_TYPES { WM,MOTOR,SENT,STORY };
	private static final String[] NOISE_STRINGS = { "Rnoise","Pnoise" };
	private static final String[] TASK_STRINGS = { "Wrkmem","Motort","Sentnc","StoryM" };
	private static final String[] OTHER_STRINGS = { "Restin", "Biocal" };
	static final String NO_EPRIME_STR = "Uploader found no e-prime files for this scan";

	// Per e-mail from Abbas, only series descriptions with "_B" should have EEG files attached
	private static final String EEG_INCLUDE = "_B";
    static {
    	
        final Map<String,String> tmap = new HashMap<String,String>();
        tmap.put("RNois","Rnoise");
        tmap.put("PNois","Pnoise");
        tmap.put("BioCl","Biocal");
        tmap.put("Rest","Restin");
        tmap.put("WkMem","Wrkmem");
        tmap.put("Motor","Motort");
        tmap.put("Sentn","Sentnc");
        tmap.put("Story","StoryM");
        convertMap = Collections.unmodifiableMap(tmap);
    }
    
	private static final Comparator<File> eprimeFileCompare = new Comparator<File>() {
		@Override
		public int compare(File arg0, File arg1) {
			final Date d0 = getDateFromEprimeFile(arg0);
			final Date d1 = getDateFromEprimeFile(arg1);
			if (d0!=null && d1!=null && !d0.equals(d1)) {
				return d0.compareTo(d1);
			} else {
				return arg0.getName().compareTo(arg1.getName());
			}
		}
	};
	
	private static final Comparator<XnatMegscandataBean> scanCompare = new Comparator<XnatMegscandataBean>() {
		@Override
		public int compare(XnatMegscandataBean arg0, XnatMegscandataBean arg1) {
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

	private static Date getDateFromEprimeFile(File zipFile) {
		final Matcher matcher = EPRIME_DP.matcher(zipFile.getName());
		if (matcher.find()) {
			try {
				return EPRIME_DF.parse(matcher.group().substring(1));
			} catch (ParseException e1) { }
		}
		return null;
	}
	
	/*
	 * Create MEG Representation from tocf file contained in the archive.  Builds archive session from the representation.
	 * @author Mike Hodge <hodgem@mir.wustl.edu>
	 */
	/*
	public HCPMegRepresentation(File tocf) throws ClientException {
		
		try {
			returnList.add("NOTE:  Upload is in older (tocf.txt) format");
			final BufferedReader reader = new BufferedReader(new FileReader(tocf));
			this.tocFile = tocf;
			try {
				String line;
				boolean insideRun = false;
				int pdfCount = 0;
				MEGSession currentSession=null;
				MEGScan currentScan=null;
				MEGRun currentRun=null;
				
				// The first line contains exeriment label
				this.expLbl = reader.readLine();
				this.megDirectory = tocf.getParentFile();
				
				while ((line = reader.readLine()) != null) {
					String[] lineComps = line.split("	");
					
					if (lineComps.length>=4 && lineComps[0].equals("PATIENT")) {
						
						this.patientID = dequote(lineComps[2]);
						this.subjectLbl = dequote(lineComps[3]);
						this.scanList = new ArrayList<MEGScan>();
						
					} else if (lineComps.length>=4 && lineComps[1].equals("SCAN")) {
						
						final MEGScan megScan = new MEGScan();
						megScan.setScanID(dequote(lineComps[2]));
						megScan.setScanLbl(dequote(lineComps[3]));
						megScan.setSessionList(new ArrayList<MEGSession>());
						scanList.add(megScan);
						
					} else if (lineComps.length>=5 && lineComps[2].equals("SESSION")) {
						
						final MEGSession  megSession = new MEGSession();
						megSession.setSessionID(dequote(lineComps[3]));
						megSession.setSessionDateStr(dequote(lineComps[4]));
						megSession.setRunList(new ArrayList<MEGRun>());
						currentScan = scanList.get(scanList.size()-1);
						currentScan.getSessionList().add(megSession);
						
					} else if (lineComps.length>=6 && lineComps[3].equals("RUN")) {
						
						insideRun = true;
						pdfCount = 0;
						final MEGRun megRun = new MEGRun();
						megRun.setRunID(dequote(lineComps[4]));
						megRun.setRunLbl(dequote(lineComps[5]));
						megRun.setFileList(new ArrayList<MEGFile>());
						currentSession = currentScan.getSessionList().get(currentScan.getSessionList().size()-1); 
						currentSession.getRunList().add(megRun);
						
					} else if (lineComps.length>=4 && lineComps[3].equals("/RUN")) {
						
						insideRun = false;
						
					} else if (insideRun) {
						
						final MEGFile megFile = new MEGFile();
						megFile.setFileType(dequote(lineComps[4]));
						if (megFile.getFileType().equals("CONFIG")) {
							megFile.setFileIn("config");
							megFile.setFileOut("config");
						} else if (megFile.getFileType().equals("HS_FILE")) {
							megFile.setFileIn("hs_file");
							megFile.setFileOut("hs_file");
						} else if (megFile.getFileType().equals("PDF")) {
							// Note:  filename starts from 0, so set this pre-increment
							megFile.setFileIn(Integer.toString(pdfCount));
							megFile.setFileOut(dequote(lineComps[5]));
							pdfCount++;
						} else {
							throw new ClientException("ERROR:  Unexpected tocf file type (" + megFile.getFileType() + ")",new Exception());
						}
						currentRun = currentSession.getRunList().get(currentSession.getRunList().size()-1); 
						currentRun.getFileList().add(megFile);
						
					}
					
				}
				reader.close();
				// re-order sessions within scans by date, then order scans by containing session date
				for (final MEGScan scan : scanList) {
					Collections.sort(scan.getSessionList());
				}
				Collections.sort(scanList);
				
			} catch (IOException e) {
				throw new ClientException(e.getMessage(),new Exception());
			} finally {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			throw new ClientException(e.getMessage(),new Exception());
		}
		
	}*/
	
	/*
	 * Create MEG Representation from newer format (no tocf file).  Builds archive session from the representation.
	 * @author Mike Hodge <hodgem@mir.wustl.edu>
	 */
	public HCPMegRepresentation(UserI user, XnatProjectdata proj, final String cachepath, String SubjID) throws ClientException {
		final ArrayList<File> fileList = getFileListFromDir(new File(cachepath));

		this.proj = proj;
		this.user = user;
		this.subjectLbl = SubjID;
		this.expLbl = newExpLbl();
		this.cachepath = cachepath;

		//this.dirStructure = getMegDirStructure(zipList);

		// construct a scan list:
		this.scanList = new ArrayList<MEGExperiment>();
		final MEGExperiment megScan = new MEGExperiment();
		megScan.setExpID(proj.getId());
		megScan.setExpSubj(SubjID);
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

	private void populateScanTypeMaps(XnatMegscandataBean scan) {
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
		addScanToScanTypeMap(scan,stype,scanTypeMap);
		if (scan.getQuality().equals("unusable")) {
			return;
		}
		addScanToScanTypeMap(scan,stype,usableScanTypeMap);
	}

	private void addScanToScanTypeMap(XnatMegscandataBean scan, String stype, Map<String, TreeSet<XnatMegscandataBean>> imap) {
		if (!imap.containsKey(stype)) {
			final TreeSet<XnatMegscandataBean> sset = new TreeSet<XnatMegscandataBean>(scanCompare);
			sset.add(scan);
			imap.put(stype,sset);
		} else {
			imap.get(stype).add(scan);			
		}
		
	}

	private static String getFileExtension(File file)
	{
        String fileName = file.getName();
        if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
        return fileName.substring(fileName.lastIndexOf(".")+1);
		else return "";
	}

	private SortedMap<File, SortedMap<File, ArrayList<File>>> getMegDirStructure(final ArrayList<File> zipList) throws ClientException {
		
		//this.subjectLbl = null;
		
		final Comparator<File> zipFileCompare = new Comparator<File>() {
			@Override
			public int compare(File arg0, File arg1) {
				// Some comparisons will be of the same directory
				if (arg0 == arg1 || arg0.equals(arg1)) {
					return 0;
				}
				// directory beneath should be date named according to date and time
				if (arg0.list().length==1 && arg1.list().length==1) {
					final Date d0 = dirToDateTime(arg0.listFiles()[0]);
					final Date d1 = dirToDateTime(arg1.listFiles()[0]);
					if (d0!=null && d1!=null) {
						return d0.compareTo(d1);
					}
				}
				return arg0.getName().compareTo(arg1.getName());
			}
		};
		
		final SortedMap<File, SortedMap<File,ArrayList<File>>> returnMap = new TreeMap<File,SortedMap<File,ArrayList<File>>>(zipFileCompare);
		
		final Iterator<File> zipI = zipList.iterator();
		while (zipI.hasNext()) {	
			final File zipFile=zipI.next();
			if (isMegFile(zipFile)) {
				final File megParent = zipFile.getParentFile();
				if (megParent==null) { 
					throw new ClientException("MEG File found in unexpected directory location (No parent directory)");
				}
				final File megGGParent = (megParent.getParentFile()!=null) ? megParent.getParentFile().getParentFile() : null;
				if (megGGParent==null) { 
					throw new ClientException("MEG File found in unexpected directory location (No GG parent directory)");
				}
				if (megGGParent.getParentFile()==null) { 
					throw new ClientException("MEG File found in unexpected directory location (MEG files should reside under a directory beginning with the subject label)");
				}
				if (this.subjectLbl == null) {
					this.subjectLbl = megGGParent.getParentFile().getName().replaceFirst("_.*$","");
					this.expLbl = newExpLbl();
				}
				if (!returnMap.keySet().contains(megGGParent)) {
					final SortedMap<File,ArrayList<File>> parentMap = new TreeMap<File,ArrayList<File>>(zipFileCompare);
					final ArrayList<File> megList = new ArrayList<File>();
					megList.add(zipFile);
					zipI.remove();
					parentMap.put(megParent,megList);
					returnMap.put(megGGParent,parentMap);
				} else if (!returnMap.get(megGGParent).keySet().contains(megParent)){
					final ArrayList<File> megList = new ArrayList<File>();
					megList.add(zipFile);
					zipI.remove();
					returnMap.get(megGGParent).put(megParent,megList);
				} else {
					returnMap.get(megGGParent).get(megParent).add(zipFile);
					zipI.remove();
				}
			}
		}
		
		return returnMap;
		
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
		try {
			Process process = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", Integer.toString(i-1), "-sid", "newExpLbl").start();
		}
		catch (IOException e){};
        return tryLbl.toString();
	}

	// need to re-write this for KIT
	private boolean isMegFile(File zipFile) {
		if (!zipFile.isFile()) {
			return false;
		}
		for (final String compare : Arrays.asList(MEG_FILES)) {
			
			if (zipFile.getName().matches(compare)) {
				return true;
			}
		}
		for (final File f : zipFile.getParentFile().listFiles()) {
			if (f.equals(zipFile)) {
				continue;
			}
			for (final String compare : Arrays.asList(MEG_FILES)) {
				if (zipFile.getName().matches(compare)) {
					return true;
				}
			}
		}
		return false;
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
	
	public class MEGExperiment implements Comparable<HCPMegRepresentation.MEGExperiment> {
		
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

	public class MEGScan implements Comparable<HCPMegRepresentation.MEGScan> {
		
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
	
	public class MEGSession implements Comparable<HCPMegRepresentation.MEGSession>
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
	
	
	public List<String> buildMegArchiveSession(XnatProjectdata proj, UserI user) throws ClientException
	{	
		this.proj = proj;
		
		
		//if (!destination.exists()) {
		//	throw new ClientException("ERROR:  Destination directory does not exist.",new Exception());
		//}
		// the location in the archive where the data will go
		/*
		final File sessionDir = new File(destination, expLbl + File.separator + "SCANS");
		try
		{
			returnList.add(sessionDir.getCanonicalPath());
		}
		catch (IOException e){}
		
		sessionDir.mkdirs();*/
		/*
		File resourceDir = null;
		// For now, don't create INFO resource when building under the new format
		
		if (dirStructure==null || dirStructure.keySet().size()<=0) {
			resourceDir = new File(destination,expLbl + File.separator + "RESOURCES" + File.separator + "INFO");
			resourceDir.mkdirs();
		}*/
		
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
	
	private Date dirToDateTime(File dir) {
		if (dir.isDirectory()) {
			try {
				final Date returnDate = DDT_DF.parse(dir.getName().replaceAll(DDT_SUBST,"%"));
				if (returnDate!=null) {
					return returnDate;
				}
			} catch (ParseException e) { }
		} 
		return null;
	}
		
	private Date dirStructureToDate(SortedMap<File, SortedMap<File, ArrayList<File>>> dirStructure) {
		for (final File f1 : dirStructure.keySet()) {
			for (final File f2 : dirStructure.get(f1).keySet()) {
				final String dirName = f2.getParentFile().getName();
				try {
					final Date returnDate = STD_DF.parse(dirName);
					if (returnDate!=null) {
						return returnDate;
					}
				} catch (ParseException e) {
					// Do nothing, try next file
				}
			}
		}
		return null;
	}
	
	public Date ReadConFileDate(File conFile) throws FileNotFoundException
	{
		// Map<String, Object>
		// Read the con file and return some information
		final String date_out;
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
	
	private void addNoteToScan(XnatMegscandataBean scan) {
		if ((scan.getQuality()!=null && scan.getQuality().equalsIgnoreCase("unusable")) ||
				(scan.getSeriesDescription()!=null && scan.getSeriesDescription().matches("^.*[0-9]$"))) {
			return;
		}
		final String currNote = scan.getNote();
		scan.setNote((currNote==null || currNote.length()<1)  ? NO_EPRIME_STR : currNote + ", " + NO_EPRIME_STR);
	}

	private void writeCatalogXML(CatCatalogBean cat, XnatResourcecatalogBean rcat, File runInfoFile, File dir) throws ClientException {
		try {
			// ensure the directory exists
			Process processa = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", runInfoFile.toString(), "-sid", "writecatxml_a").start();
			runInfoFile.getParentFile().mkdirs();
			final FileWriter fw = new FileWriter(runInfoFile);
			cat.toXML(fw);
			fw.close();
			// Set URI to archive path
			final String path = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc() + File.separator + expLbl +
				File.separator + "SCANS" + File.separator + Utils.getRelativeURI(dir,runInfoFile);
			Process processb = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", path.toString(), "-sid", "writecatxml_b").start();
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

	private int getRunNo(String seriesDesc, HashMap<String, Integer> sdMap) {
		Integer i = sdMap.get(seriesDesc);
		i = (i==null) ? 1 : i+1;
		sdMap.put(seriesDesc,i);
		return i;
	}

	private String getBaseDescriptionFromFileName(String name) {
		for (final String conv : convertMap.keySet()) {
			if (name.contains(conv)) {
				return convertMap.get(conv);
			}
		}
		return name;
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
						Process process = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m",mfile.getFileIn().toString(), "-sid", "path_in").start();
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
				writeCatalogXML(cat, rcat, catFile, destPath);		// dest path *shouldn't* be needed...
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
			Process process = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", destFile.toString(), "-sid", "xml").start();
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
			Process processb = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", new_id.toString(), "-sid", "new_id").start();
			megdat.setId(new_id);
			
			final PersistentWorkflowI wrk = PersistentWorkflowUtils.buildOpenWorkflow(user, megdat.getItem(),
					EventUtils.newEventInstance(EventUtils.CATEGORY.DATA, EventUtils.TYPE.WEB_SERVICE, EventUtils.CREATE_VIA_WEB_SERVICE, null, null));
			final EventMetaI ci = wrk.buildEvent();

			final String expectedPath=megdat.getExpectedSessionDir().getAbsolutePath().replace('\\', '/');
			Process process2 = new ProcessBuilder("/usr/bin/python3", "/home/ec2-user/pylog.py", "-m", expectedPath.toString(), "-sid", "expected").start();		// ~/arc001/Session_X

			if (SaveItemHelper.authorizedSave(megdat,user,false,false,ci)) {
				PersistentWorkflowUtils.complete(wrk, ci);
				final String p = ArcSpecManager.GetInstance().getArchivePathForProject(proj.getId()) + proj.getCurrentArc();
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
