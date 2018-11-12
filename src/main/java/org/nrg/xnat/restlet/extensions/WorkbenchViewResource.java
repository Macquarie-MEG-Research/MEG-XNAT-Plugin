package org.nrg.xnat.restlet.extensions;

import java.io.File;
import java.io.FileWriter;

import javax.servlet.http.HttpServletRequest;

import org.nrg.xdat.om.XdatStoredSearch;
import org.nrg.xnat.restlet.resources.ItemResource;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.restlet.XnatRestlet;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Variant;

@XnatRestlet({"/workbench/view/{SEARCH_ID}","/workbench/url/{SEARCH_ID}"})
public class WorkbenchViewResource extends ItemResource {
	
	XdatStoredSearch xss = null;
	String sID=null;
	boolean returnFile=true;

	public WorkbenchViewResource(Context context, Request request,
			Response response) {
		super(context, request, response);
		
		sID= (String)request.getAttributes().get("SEARCH_ID");
		if (request.getResourceRef().getSegments().contains("url")) {
			returnFile=false;
		}
		if(sID!=null){
			this.getVariants().add(new Variant(MediaType.TEXT_XML));
		}else{
			response.setStatus(Status.CLIENT_ERROR_GONE);
		}
	}
			
	
	@Override
	public boolean allowGet() {
		return true;
	}

	@Override
	public void handleGet() {
		try {
			// For now, we're just returning a file containing the search ID
			String userPath = ArcSpecManager.GetInstance().getGlobalCachePath() + "USERS" + File.separator + getUser().getID();
			File userDir = new File(userPath);
			if (!userDir.exists()) {
				userDir.mkdirs();
			}	
			if (!returnFile) {
				HttpServletRequest httpReq=this.getHttpServletRequest();
				this.returnString(httpReq.getScheme() + "://" + httpReq.getServerName() + ":" + httpReq.getServerPort() +
						"/data/services/cifti-average?searchID=" + sID, Status.SUCCESS_OK);
				return;
			}
			File outF = new File(userPath,sID + ".wb");
			FileWriter fw = new FileWriter(outF);
			fw.write(sID);
			fw.close();
			this.setResponseHeader("Cache-Control", "must-revalidate");
			this.getResponse().setEntity(this.representFile(outF,MediaType.TEXT_PLAIN));			
			this.getResponse().setStatus(Status.SUCCESS_OK);
		} catch (Exception e) {
			e.printStackTrace();
			this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL);
		}
	}

}
