package org.nrg.mqmeg.importer;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.mqmeg.importer.utils.MegRepresentation;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import java.lang.ProcessBuilder;
import org.nrg.xdat.om.base.auto.AutoXnatProjectdata;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.actions.importer.ImporterHandler;
import org.nrg.xnat.restlet.actions.importer.ImporterHandlerA;
import org.nrg.xnat.restlet.util.FileWriterWrapperI;
import org.python.google.common.collect.Lists;

/**
 * Imports a zip file containing an MEG session and creates an mrSession 
 * @author Mike Hodge <hodgem@mir.wustl.edu>
 *
 */
@ImporterHandler(handler = "MEG", allowCallsWithoutFiles = true, callPartialUriWrap = false)
public class MegImporter extends ImporterHandlerA implements Callable<List<String>> {

	static final String[] zipExtensions={".zip",".jar",".rar",".ear",".gar",".xar"};

	static Logger logger = Logger.getLogger(MegImporter.class);

	private final FileWriterWrapperI fw;
    private final UserI user;
    private final XnatSubjectdata subject;
	final Map<String,Object> params;
    private XnatProjectdata proj;
	
	/**
	 * 
	 * @param listenerControl
	 * @param u
	 * @param session
	 * @param overwrite:   'append' means overwrite, but preserve un-modified content (don't delete anything)
	 *                      'delete' means delete the pre-existing content.
	 * @param additionalValues: should include project (subject and experiment are expected to be found in the archive)
	 */
	public MegImporter(Object listenerControl, UserI u, FileWriterWrapperI fw, Object subject, Map<String, Object> params) {
		super(listenerControl, u, fw, params);
		this.user=u;
        this.fw=fw;
        this.subject = XnatSubjectdata.getXnatSubjectdatasById(subject, user, true);
		this.params=params;
	}
	
	public MegImporter(UserI u, Object subject, Map<String, Object> params) {
		this(null, u, null, subject, params);
	}

	@Override
	public List<String> call() throws ClientException, ServerException {
		verifyProjectAndSubject();
		try {
			final List<String> returnList = Lists.newArrayList();
			returnList.addAll(getFileFromBuildLocationAndProcess(params.get("BuildPath").toString()));
			this.completed("Successfully imported MEG Session");
			return returnList;
		} catch (ClientException e) {
			logger.error("Couldn't save session",e);
			this.failed(e.getMessage());
			throw e;
		} catch (ServerException e) {
			logger.error("Couldn't save session",e);
			this.failed(e.getMessage());
			throw e;
		} catch (Throwable e) {
			logger.error("Couldn't save session",e);
			throw new ServerException(e.getMessage(),new Exception());
		}
	}

	private Collection<? extends String> getFileFromBuildLocationAndProcess(String buildPath) throws ClientException, ServerException {
		
		if (buildPath==null) {
			clientFailed("ERROR:  Build path was not specified");
		}
		File buildDir = new File(buildPath);
		if (!buildDir.isDirectory()) {
			clientFailed("ERROR:  Build path does not exist or is not a directory");
		}
		return processMegFolder(buildDir);
	}

	private void verifyProjectAndSubject() throws ClientException {
		if (params.get("project") == null) {
			clientFailed("ERROR:  project parameter must be supplied for import");
		}
		String projID=params.get("project").toString();
		// NOTE:  Changing preload parameter to true.  Required to run via automation script
		proj=AutoXnatProjectdata.getXnatProjectdatasById(projID, user, true);
		if (proj == null) {
			clientFailed("ERROR:  Project specified is invalid or user does not have access to project");
		}
	}
	

	private void clientFailed(String fmsg) throws ClientException {
		this.failed(fmsg);
		throw new ClientException(fmsg,new Exception());
	}

    /**
     * Process the MEG folder.
     * We take the information passed to this class to the MegRepresentation class.
     * This class then has the buildMegArchiveSession method called to actually generate all the XNAT session info and associate it with the files
     */
    private List<String> processMegFolder(File buildDir) throws ClientException, ServerException
    {
        String[] fileList=buildDir.list();
        if (fileList==null || fileList.length==0) {
        	throw new ClientException("Archive file contains no files.");
        }

        try {
        	final MegRepresentation megRep = new MegRepresentation(user, proj, subject, buildDir.getAbsolutePath());
        	return megRep.buildMegArchiveSession(proj, user);
        } catch (ClientException e) {
        	throw new ClientException("ERROR: Could not build session from uploaded file.  Please check file.", e);
        }
        
	}

}
