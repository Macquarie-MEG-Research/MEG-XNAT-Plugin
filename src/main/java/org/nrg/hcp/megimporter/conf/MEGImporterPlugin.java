package org.nrg.hcp.megimporter.conf;

import org.apache.log4j.Logger;
import org.nrg.framework.annotations.XnatPlugin;
import org.springframework.context.annotation.ComponentScan;

@XnatPlugin(
			value = "MQMEGImporterPlugin",
			name = "Macquarie University MEG importer plugin",
			description = "Upload KIT MEG data"
		)
@ComponentScan({ 
	"org.nrg.hcp.megimporter.components"
	})
public class MEGImporterPlugin {
	
	/** The logger. */
	public static Logger logger = Logger.getLogger(MEGImporterPlugin.class);

	/**
	 * Instantiates a new MEG importer plugin
	 */
	public MEGImporterPlugin() {
		logger.info("Configuring MEG importer plugin");
	}
	
}
