package org.nrg.hcp.ajax;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.turbine.util.TurbineException;
import org.nrg.xdat.XDAT;
import org.nrg.xft.XFTTable;
import org.nrg.xft.exception.DBPoolException;
import org.nrg.xft.security.UserI;

public class ProjectRestrictionGetter {
		
	static org.apache.log4j.Logger logger = Logger.getLogger(ProjectRestrictionGetter.class);
			
	private String project;
	private String restrictProjectDesc;
	private boolean restrictSubjectCreation;
	private boolean restrictAssessorCreation;
	
	private void initializeVars(HttpServletRequest request, HttpServletResponse response, ServletConfig sc) throws TurbineException, SQLException, DBPoolException, IOException { 
		
		// Only one response type for this class & default getter
		response.setContentType("text/plain");
		
		this.project = request.getParameter("project");
		this.restrictProjectDesc = "";
		this.restrictSubjectCreation=false;
		this.restrictAssessorCreation=false;
		
		// Run query to retrieve latest (max) ID
		final UserI user=XDAT.getUserDetails();
		
		// retrieve subject label projectData fields from DB
		final StringBuilder querySB=new StringBuilder("select name,field from xnat_projectdata_field where fields_field_xnat_projectdata_id='");
		querySB.append(project);
		querySB.append("' and name in ('restrictsubjectcreation','restrictassessorcreation','restrictprojectdesc')"); 
		XFTTable slTable=XFTTable.Execute(querySB.toString(), user.getDBName(), user.getLogin());
		if (slTable==null || !slTable.hasMoreRows()) {
			// return if no project level auto-generate fields defined
			response.getWriter().write("");
			return;
		}
		// assing DB values to method values
		while (slTable.hasMoreRows()) {
			final Object[] rarray = slTable.nextRow();
			
			if (rarray[0].toString().equalsIgnoreCase("restrictsubjectcreation")) {
				restrictSubjectCreation=((rarray[1]!=null && rarray[1].toString().equals("1")));
			} else if (rarray[0].toString().equalsIgnoreCase("restrictassessorcreation")) {
				restrictAssessorCreation=((rarray[1]!=null && rarray[1].toString().equals("1")));
			} else if (rarray[0].toString().equalsIgnoreCase("restrictprojectdesc")) {
				if (rarray[1]!=null)
					restrictProjectDesc=rarray[1].toString();
			}	
		}
	}
		
	public void getRestrictProjectDesc(HttpServletRequest request, HttpServletResponse response, ServletConfig sc) { 
		
		try {
			
			this.initializeVars(request, response, sc);
			
			// Immediately expire headers
			response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
			response.setHeader("Pragma","no-cache"); //HTTP 1.0
			response.setDateHeader ("Expires", -1); //prevents caching at the proxy serve
			
			response.getWriter().write(restrictProjectDesc);
			
		} catch (TurbineException e) {
			handleException(e,response);
		} catch (SQLException e) {
			handleException(e,response);
		} catch (DBPoolException e) {
			handleException(e,response);
		} catch (IOException e) {
			handleException(e,response);
		}
		
	}
		
	public void getSubjectRestrictionStatus(HttpServletRequest request, HttpServletResponse response, ServletConfig sc) { 
		
		try {
			
			this.initializeVars(request, response, sc);
			
			// Immediately expire headers
			response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
			response.setHeader("Pragma","no-cache"); //HTTP 1.0
			response.setDateHeader ("Expires", -1); //prevents caching at the proxy serve
			
			response.getWriter().write(String.valueOf(restrictSubjectCreation));
			
		} catch (TurbineException e) {
			handleException(e,response);
		} catch (SQLException e) {
			handleException(e,response);
		} catch (DBPoolException e) {
			handleException(e,response);
		} catch (IOException e) {
			handleException(e,response);
		}
		
	}
		
	public void getAssessorRestrictionStatus(HttpServletRequest request, HttpServletResponse response, ServletConfig sc) { 
		
		try {
			
			this.initializeVars(request, response, sc);
			
			// Immediately expire headers
			response.setHeader("Cache-Control","no-cache"); //HTTP 1.1
			response.setHeader("Pragma","no-cache"); //HTTP 1.0
			response.setDateHeader ("Expires", -1); //prevents caching at the proxy serve
			
			response.getWriter().write(String.valueOf(restrictAssessorCreation));
			
		} catch (TurbineException e) {
			handleException(e,response);
		} catch (SQLException e) {
			handleException(e,response);
		} catch (DBPoolException e) {
			handleException(e,response);
		} catch (IOException e) {
			handleException(e,response);
		}
		
	}
	
	private void handleException(Exception e,HttpServletResponse response) {
		
		logger.error("",e);
		try {	
			response.getWriter().write("");
		} catch (IOException e1) {
			logger.error("",e1);
		}
		
	}
	
}
