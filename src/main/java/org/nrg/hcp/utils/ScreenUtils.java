package org.nrg.hcp.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.turbine.util.RunData;
import org.nrg.pipeline.PipelineManager;
import org.nrg.xdat.model.ArcProjectDescendantPipelineI;

/**
* @author Tim
*
*/
public class ScreenUtils {
	
	private static final Map<String,List<String>> projectPipelines = new HashMap<String,List<String>>();

	public static List<String> getProjectPipelineNames(String projectId, String xsiType) {
		if (projectPipelines.get(projectId) == null) {
			List<String> pList = new ArrayList<String>();
			for (ArcProjectDescendantPipelineI pipeline : PipelineManager.getPipelinesForProjectDescendant(projectId, xsiType, false)) {
				pList.add(pipeline.getName());
			}
			projectPipelines.put(projectId,pList);
		}
		return projectPipelines.get(projectId);
	}

	public static List<String> getProjectPipelineNames(RunData data, String xsiType) {
		String projectId = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("project",data));
		return getProjectPipelineNames(projectId,xsiType);
	}

}
