/*
 *	Copyright Washington University in St Louis 2006
 *	All rights reserved
 *
 * 	@author Mohana Ramaratnam (Email: mramarat@wustl.edu)

*/

package org.apache.turbine.app.hcpinternal.modules.actions;

import org.apache.log4j.Logger;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.pipeline.PipelineRepositoryManager;
import org.nrg.pipeline.launchers.BirnPhantomQA;
import org.nrg.xdat.turbine.modules.actions.SecureAction;
import org.nrg.xdat.turbine.utils.AdminUtils;

public class BuildAction extends  SecureAction {

	static org.apache.log4j.Logger logger = Logger.getLogger(BuildAction.class);

	   public void doPerform(RunData data, Context context){
			if (data.getParameters().getString("refresh") !=null) {
				String refresh = data.getParameters().getString("refresh");
				if (refresh.equalsIgnoreCase("PipelineRepository")) {
					doReload(data,context);
				}
			}
	   }

	   public void doReload(RunData data, Context context){
	       try {
	    	   PipelineRepositoryManager.Reset();
	    	   String msg = "<p><b>The site wide pipeline repository has been refreshed</b></p>";
			   data.setMessage(msg);
	       }catch(Exception e) {
	           logger.debug("Unable to refresh the pipeline repository ", e);
	       }
	  }


	   public void doBirnphantomqa(RunData data, Context context){
		   BirnPhantomQA stdBuild = new BirnPhantomQA();
	     	boolean rtn = stdBuild.launch(data, context);
          if (rtn) {
        	  data.setMessage("<p><b>The build process was successfully launched.  Status email will be sent upon its completion.</b></p>");
          }else {
        	  data.setMessage("<p><b>The build process was not successfully launched.  Please contact" +AdminUtils.getAdminUser().getEmail()   + ".</b></p>");
          }
          data.setScreenTemplate("ClosePage.vm");
	   }

}
