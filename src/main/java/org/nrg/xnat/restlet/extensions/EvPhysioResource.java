package org.nrg.xnat.restlet.extensions;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.nrg.hcp.linkeddata.importer.HCPLinkedDataImporter;
import org.nrg.hcp.linkeddata.utils.HCPScriptExecUtils;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.model.XnatImagescandataI;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatMrsessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatSubjectdata;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.search.CriteriaCollection;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.StringRepresentation;


@XnatRestlet({	
		"/projects/{PROJECT_ID}/subjects/{SUBJECT_ID}/experiments/{EXP_ID}/evphysiogen",
		"/experiments/{EXP_ID}/evphysiogen",
		})
public class EvPhysioResource extends SecureResource {
	
	private XnatProjectdata proj = null;
	private XnatSubjectdata subj = null;
	private XnatExperimentdata exp = null;
	private static final List<String> sessionEmailList = new ArrayList<String>();
	
	final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(EvPhysioResource.class);

	public EvPhysioResource(Context context, Request request, Response response) {
		super(context, request, response);

		final String projID = (String)getParameter(request,"PROJECT_ID");
		final String subjID = (String)getParameter(request,"SUBJECT_ID");
		final String exptID = (String)getParameter(request,"EXP_ID");
		if (projID!=null) {
			proj = XnatProjectdata.getProjectByIDorAlias(projID, getUser(), false);
			if (proj == null) {
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND,"Specified project either does not exist or user is not permitted access to subject data");
			}
			subj = XnatSubjectdata.GetSubjectByProjectIdentifier(proj.getId(), subjID, getUser(), false);
			if (subj==null) {
				subj = XnatSubjectdata.getXnatSubjectdatasById(subjID, getUser(), false);
			}
			if (subj != null && (proj != null && !subj.hasProject(proj.getId()))) {
				subj = null;
			}
			if (subj == null) {
				response.setStatus(Status.CLIENT_ERROR_NOT_FOUND,"Specified subject either does not exist or user is not permitted access to subject data");
			}
			exp = XnatExperimentdata.getXnatExperimentdatasById(exptID, getUser(), false);
			if (exp != null && (proj != null && !exp.hasProject(proj.getId()))) {
				exp = null;
			}
			if(exp==null){
				exp = (XnatExperimentdata)XnatExperimentdata.GetExptByProjectIdentifier(proj.getId(),exptID, getUser(), false);
			}
			
		} else {
			exp = XnatExperimentdata.getXnatExperimentdatasById(exptID, getUser(), false);
			proj = exp!=null ? exp.getProjectData() : null;
		}
		if (exp==null) {
			response.setStatus(Status.CLIENT_ERROR_NOT_FOUND,"ERROR:  Could not locate specified experiment");
			return;
		}
		if (!exp.getClass().isAssignableFrom(XnatMrsessiondata.class)) {
			final String rMsg = "ERROR:  Experiment is wrong type for this URL (valid only for xnat:mrSessionData)";
			getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST,rMsg);
			return;
		}
		try {
			if (!proj.canEdit(getUser())) { 
				response.setStatus(Status.CLIENT_ERROR_UNAUTHORIZED,"User account is not permitted to access this service");
				return;
			}
		} catch (Exception e) {
			response.setStatus(Status.SERVER_ERROR_INTERNAL,"INTERNAL ERROR:  Could not evaluate user permissions");
			return;
		}
	}

	@Override
	public boolean allowGet() {
		return false;
	}

	@Override
	public boolean allowPut() {
		return true;
	}

	@Override
	public void handlePut() {
		final List<String> returnList = new ArrayList<String>();;
		boolean doEV = true;
		boolean doPhysio = true;
		boolean runSanityChecks = false;
		boolean sendEmail = false;
		boolean returnStatusText = false;
		if (hasQueryVariable("ev") && getQueryVariable("ev").toString().equalsIgnoreCase("false")) {
			doEV = false;
		}
		if (hasQueryVariable("physio") && getQueryVariable("physio").toString().equalsIgnoreCase("false")) {
			doPhysio = false;
		}
		if (hasQueryVariable("runSanityChecks") && getQueryVariable("runSanityChecks").toString().equalsIgnoreCase("true")) {
			runSanityChecks = true;
		}
		if (hasQueryVariable("sendEmail") && getQueryVariable("sendEmail").toString().equalsIgnoreCase("true")) {
			sendEmail = true;
		}
		if (hasQueryVariable("returnStatusText") && getQueryVariable("returnStatusText").toString().equalsIgnoreCase("true")) {
			returnStatusText = true;
		}
		ArrayList<XnatImagescandataI> ldScans = new ArrayList<XnatImagescandataI>();
		for (XnatImagescandataI scan : ((XnatMrsessiondata)exp).getScans_scan()) {
			for (final XnatAbstractresourceI rs : scan.getFile()) {
				if (rs.getLabel().equals(HCPLinkedDataImporter.LINKED_RESOURCE_LABEL)) {
					ldScans.add(scan);
					break;
				}
			}
		}
		returnList.add("<br><b>BEGIN GENERATION OF EV/PHYSIO FILES</b>");
		returnList.addAll(HCPScriptExecUtils.generateEVandPhysioTxtFiles(getUser(), proj, (XnatMrsessiondata) exp, ldScans, doEV, doPhysio).getResultList());
		
		// Sanity checks
		if (runSanityChecks) {
			returnList.add("<br><b>BEGIN RUNNING OF SANITY CHECKS FOR LINKED_DATA FILES</b>");
			returnList.addAll(HCPScriptExecUtils.runSanityChecks(getUser(), proj, (XnatMrsessiondata) exp, true).getResultList());
			returnList.add("<br><b>FINISHED PROCESSING");
		}
		final String returnStr = StringUtils.join(returnList,"<br>");
		if (sendEmail) {
			sessionEmailList.add(exp.getId());
			sendUserEmail(returnStr);
		}
		if (returnStatusText) {
			getResponse().setEntity(new StringRepresentation("<br>" + returnStr));
		}
		getResponse().setStatus(Status.SUCCESS_OK);
	}
	
	
	private void sendUserEmail(String msg) {
		if (sessionEmailList.contains(exp.getId())) {
			sessionEmailList.remove(exp.getId());
			final org.nrg.xft.search.CriteriaCollection cc = new CriteriaCollection("AND");
  	      	cc.addClause("val:SanityChecks.imageSession_ID",exp.getId());
  	      	cc.addClause("val:SanityChecks.project",proj.getId());
			try {
				final StringBuilder returnSB=new StringBuilder();
					returnSB.append("<h3>EV/Physio generation processing complete:  ")
						.append("</h3>") 
						.append("<h3>MR Session:  <a href=\"") 
						.append(TurbineUtils.GetFullServerPath()) 
						.append("/app/action/DisplayItemAction/search_element/xnat:mrSessionData/search_field/xnat:mrSessionData.ID/search_value/") 
						.append(exp.getId()) 
						.append("/project/") 
						.append(proj.getId()) 
						.append("\">") 
						.append(exp.getLabel()) 
						.append("</a></h3>\n")
						.append(msg)
						; 
				AdminUtils.sendUserHTMLEmail("EV/Physio generation run is complete (SESSION=" + exp.getLabel() + ")", returnSB.toString(), false, new String[] { getUser().getEmail() });
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}

	
