package org.nrg.mqmeg.megimporter.components;

import java.util.Arrays;

import javax.annotation.PostConstruct;

import org.nrg.xnat.restlet.actions.importer.ImporterHandlerPackages;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The Class ImporterHandlerPackagesUpdater.
 * 
 * The whole purpose of this class is to update the list of packages to be searched for importers.  There's not currently
 * an XNAT annotation processer for to find ImporterHandler annotated classes. 
 */
@Component
public class ImporterHandlerPackagesUpdater {
	
	/** The hcp importer packages. */
	final String[] hcpImporterPackages = new String[] { "org.nrg.mqmeg.importer" };
	
	/**
	 * Inits the it.
	 */
	@PostConstruct
	public void initIt() {
		this.packages.addAll(Arrays.asList(hcpImporterPackages));
	}
	
	/**
	 * Instantiates a new importer handler packages updater.
	 *
	 * @param packages the packages
	 */
	@Autowired
	public ImporterHandlerPackagesUpdater(ImporterHandlerPackages packages) {
		super();
		this.packages = packages;
	}

	/** The packages. */
	public final ImporterHandlerPackages packages; 

}
