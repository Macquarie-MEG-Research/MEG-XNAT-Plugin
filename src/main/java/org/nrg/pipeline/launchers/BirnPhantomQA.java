/*
 *	Copyright Washington University in St Louis 2006
 *	All rights reserved
 *
 * 	@author Mohana Ramaratnam (Email: mramarat@wustl.edu)

*/

package org.nrg.pipeline.launchers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.pipeline.XnatPipelineLauncher;
import org.nrg.pipeline.utils.PipelineFileUtils;
import org.nrg.pipeline.xmlbeans.ParameterData;
import org.nrg.pipeline.xmlbeans.ParameterData.Values;
import org.nrg.pipeline.xmlbeans.ParametersDocument.Parameters;
import org.nrg.xdat.om.XnatMrsessiondata;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.ItemI;

public class BirnPhantomQA extends PipelineLauncher{

	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(BirnPhantomQA.class);

	public static final String  NAME = "fbirn_phantom_v1.0.xml";

	public static final String  LOCATION = "HCP_QC/BIRN";

	public static final	String BOLD = "BOLD";
	public static final String BOLD_PARAM = "bold";



	public boolean launch(RunData data, Context context) {
		boolean rtn = false;
		try {
			ItemI data_item = TurbineUtils.GetItemBySearch(data);
			XnatMrsessiondata mr = new XnatMrsessiondata(data_item);
			XnatPipelineLauncher xnatPipelineLauncher = XnatPipelineLauncher.GetLauncher(data, context, mr);
			String pipelineName = data.getParameters().get("pipelinename");
			String cmdPrefix = data.getParameters().get("cmdprefix");
			xnatPipelineLauncher.setPipelineName(pipelineName);
			String project = mr.getProject();
			String buildDir = PipelineFileUtils.getBuildDir(project, true);
			xnatPipelineLauncher.setBuildDir(buildDir);

			xnatPipelineLauncher.setParameter("sessionId", mr.getLabel());

			ArrayList<String> bold = getCheckBoxSelections(data,mr,BOLD);

			if (bold != null && bold.size() > 0 ) {
				Parameters parameters = Parameters.Factory.newInstance();
		        ParameterData param = parameters.addNewParameter();
		    	param.setName("bold");
			    Values values = param.addNewValues();
		       for (int i = 0; i < bold.size(); i++) {
		        	values.addList(bold.get(i));
		        }
				String paramFileName = getName(pipelineName);
				Date date = new Date();
			    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
			    String s = formatter.format(date);

				paramFileName += "_params_" + s + ".xml";

				String paramFilePath = saveParameters(buildDir+File.separator + mr.getLabel(),paramFileName,parameters);

				xnatPipelineLauncher.setParameterFile(paramFilePath);

				rtn = xnatPipelineLauncher.launch(cmdPrefix);
			}else {
			  return false;
			}

		}catch (Exception e) {
			logger.debug(e);
		}
		return rtn;
	}

}
