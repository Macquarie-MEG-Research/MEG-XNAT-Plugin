/**
 * Copyright (c) 2013 Washington University School of Medicine
 */
package org.nrg.hcp.cifti.controllers;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

import javax.inject.Inject;

import org.nrg.hcp.cifti.views.CIFTIMetadataXMLView;
import org.nrg.hcp.cifti.views.IndexedCIFTIView;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.utils.CatalogUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
@Controller
@RequestMapping("/cifti-average")
public final class CIFTIAverageController {
    @Inject
    private String workbench_command;

    private static final Map<String,String> suffixes = 
            ImmutableMap.of("dconn", ".dconn.nii", "dtseries", ".dtseries.nii");

    private final Logger logger = LoggerFactory.getLogger(CIFTIAverageController.class);

    private void getCatalogsMap(final XnatProjectdata project,
            final Map<String,XnatResourcecatalog> resourceCatalogs,
            final Map<String,CatCatalogBean> catalogs) {
        final String root = project.getArchiveRootPath();
        for (final XnatAbstractresourceI resourcei : project.getResources_resource()) {
            if (resourcei instanceof XnatResourcecatalog) {
                final XnatResourcecatalog rc = (XnatResourcecatalog)resourcei;
                final String id = rc.getLabel();
                resourceCatalogs.put(id, rc);
                catalogs.put(id, rc.getCatalog(root));
            }
        }
    }

    @RequestMapping(method=RequestMethod.POST)
    public ModelAndView handlePost(final Map<String,Object> model,
            @RequestParam(value="resource") final String resourceDescription,
            @RequestParam(value="row-index", required=false) final Integer row,
            @RequestParam(value="type", defaultValue="dconn") final String type,
            @RequestParam(value="metadata", defaultValue="false") final String isMetadataRequest)
                    throws NoSuchProjectException,InvalidResourceException,FileNotFoundException {
        final String[] rdx = resourceDescription.split(":");
        if (rdx.length != 3) {
            throw new InvalidResourceException(resourceDescription);
        }
        final String projectID = rdx[0];
        final String resourceID = rdx[1];
        final String basename = rdx[2];
        final UserI user = XDAT.getUserDetails();
        final XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectID, user, false);
        if (null == project) {
            throw new NoSuchProjectException(projectID);
        }

        final Map<String,XnatResourcecatalog> resourceCatalogs = Maps.newHashMap();
        final Map<String,CatCatalogBean> catalogs = Maps.newHashMap();
        getCatalogsMap(project, resourceCatalogs, catalogs);
        final XnatResourcecatalog rc = resourceCatalogs.get(resourceID);
        final File catalogXML = new File(rc.getUri());
        final String resourcePath = catalogXML.getParent();
        final CatCatalogBean catalog = catalogs.get(resourceID);
        if (null == rc || null == catalog) {
            throw new InvalidResourceException(resourceDescription);
        }
        final String name = basename + suffixes.get(type);
        final CatEntryI entry = CatalogUtils.getEntryByName(catalog, name);
        if (null == entry) {
            throw new FileNotFoundException(basename);
        }
        final File f = CatalogUtils.getFile(entry, resourcePath);
        if (logger.isTraceEnabled()) {
            logger.trace("{} {} {} {} -> {}", new Object[]{projectID, resourceID, basename, type, f});
        }
        model.put("file", f);
        model.put("row", row);
        final View view;
        if (Boolean.parseBoolean(isMetadataRequest) || "".equals(isMetadataRequest)) {
            view = new CIFTIMetadataXMLView(workbench_command);
        } else if (null == row) {
            throw new InvalidResourceException(resourceDescription + " (no row index provided)");
        } else {
            view = new IndexedCIFTIView(workbench_command);
        }
        return new ModelAndView(view, model);
    }

    private final class NoSuchProjectException extends Exception {
        private static final long serialVersionUID = -3403974088477925596L;

        NoSuchProjectException(final String name) {
            super("no project named " + name + " was found");
        }
    }

    private final class InvalidResourceException extends Exception {
        private static final long serialVersionUID = 1926710019174664016L;

        InvalidResourceException(final String path) {
            super("no resource found for " + path);
        }
    }
}
