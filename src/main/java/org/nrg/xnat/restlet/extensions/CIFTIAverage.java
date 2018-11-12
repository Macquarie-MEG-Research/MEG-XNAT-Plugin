/**
 * Copyright 2011-2013 Washington University School of Medicine
 */
package org.nrg.xnat.restlet.extensions;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.nrg.action.ClientException;
import org.nrg.hcp.restlet.services.utils.BasicParameterHandler;
import org.nrg.hcp.restlet.services.utils.IgnoreParameterHandler;
import org.nrg.hcp.restlet.services.utils.ParameterHandler;
import org.nrg.xdat.bean.CatCatalogBean;
import org.nrg.xdat.collections.DisplayFieldWrapperCollection;
import org.nrg.xdat.model.CatEntryI;
import org.nrg.xdat.model.XnatAbstractresourceI;
import org.nrg.xdat.om.XdatStoredSearch;
import org.nrg.xdat.om.XnatMrsessiondata;
import org.nrg.xdat.om.XnatProjectdata;
import org.nrg.xdat.om.XnatResourcecatalog;
import org.nrg.xdat.search.CriteriaCollection;
import org.nrg.xdat.search.DisplaySearch;
import org.nrg.xft.XFTTableI;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.restlet.XnatRestlet;
import org.nrg.xnat.restlet.resources.SecureResource;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.utils.CatalogUtils;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Computes an averaged connectome for subjects in the provided stored search.
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
@XnatRestlet("/services/cifti-average")
public final class CIFTIAverage extends SecureResource {
    public static final String SEARCH_ID_KEY = "searchID";
    public static final String CONN_TYPE_KEY = "type";
    public static final String PROJECT_KEY = "project";
    public static final String RESOURCE_KEY = "resource";
    public static final MediaType CIFTI_AVERAGE = new MediaType("application/x-caret-cifti-average");
    public static final MediaType CIFTI_METADATA = new MediaType("application/x-caret-cifti-metadata");

    private static final Map<String,ParameterHandler> paramHandlers = new ImmutableMap.Builder<String,ParameterHandler>()
            .put("project", new IgnoreParameterHandler())
            .put("resource", new IgnoreParameterHandler())
            .put("searchID", new IgnoreParameterHandler())
            .put("type", new IgnoreParameterHandler())
            .put("metadata", new IgnoreParameterHandler())
            .put("column-index", new BasicParameterHandler("-select-column-index"))
            .put("column-surface-node", new BasicParameterHandler("-select-column-surface-node", ",", 2))
            .put("column-voxel-ijk", new BasicParameterHandler("-select-column-voxel-ijk", ",", 4))
            .put("column-voxel-xyz", new BasicParameterHandler("-select-column-voxel-xyz", ",", 3))
            .put("row-index", new BasicParameterHandler("-select-row-index"))
            .put("row-surface-node", new BasicParameterHandler("-select-row-surface-node", ",", 2))
            .put("row-voxel-ijk", new BasicParameterHandler("-select-row-voxel-ijk", ",", 4))
            .put("row-voxel-xyz", new BasicParameterHandler("-select-row-voxel-xyz", ",", 3))
            .build();

    private static enum ConnectomeType {
        dconn, dtseries
    };

    private static final Map<ConnectomeType,String> suffixes = 
            ImmutableMap.of(ConnectomeType.dconn, ".dconn.nii", ConnectomeType.dtseries, ".dtseries.nii");

    private static final Map<ConnectomeType,String> singleSubjectFileNames =
            ImmutableMap.of(ConnectomeType.dconn, "Run1", ConnectomeType.dtseries, "Run1_tcs");

    private final Logger logger = LoggerFactory.getLogger(CIFTIAverage.class);
    private final Multimap<String,Object> params = LinkedHashMultimap.create();

    /*
     * 
     */
    public CIFTIAverage(final Context context, final Request request, final Response response) {
        super(context, request, response);
    }

    @Override
    public boolean allowGet() { return true; }

    @Override
    public boolean allowPost() { return true; }

    @Override
    public void handleGet() {
        final StringBuilder sb = new StringBuilder("<html>");
        sb.append("<h2>CIFTI averaging service</h2>");
        sb.append("<p>POST with query parameters to use this service.</p>");
        sb.append("<h3>query parameters:</h3>");
        for (final String key : paramHandlers.keySet()) {
            sb.append("<p>").append(key).append("</p>");
        }
        sb.append("</html>");
        this.getResponse().setEntity(sb.toString(), MediaType.TEXT_HTML);
    }

    @Override
    public void handleParam(final String key, final Object value) {
        params.put(key, value);
    }

    private Set<String> getParamValues(final String key) {
        final Set<String> vals = Sets.newLinkedHashSet();
        final Collection<?> raws = params.get(key);
        if (null != raws) {
            for (final Object o : raws) {
                vals.add(o.toString());
            }
        }
        return vals;
    }

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

    @Override
    public void handlePost() {
        try {
            loadQueryVariables();
        } catch (ClientException e) {
            logger.error("unable to load query parameters", e);
            this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "unable to parse query parameters: " + e.getMessage());
            return;
        }

        final ConnectomeType type = getType();

        final List<File> files = Lists.newArrayList();
        if (params.containsKey(SEARCH_ID_KEY)) {
            try {
                files.addAll(getCIFTIFiles(getUser(), params.get(SEARCH_ID_KEY), type));
            } catch (Throwable t) {
                logger.error("unable to perform search", t);
                this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, "unable to perform search: " + t.getMessage());
                return;
            }
        }
        if (params.containsKey(PROJECT_KEY)) {
            final String projectID;
            try {
                projectID = (String)Iterables.getOnlyElement(params.get(PROJECT_KEY));
            } catch (NoSuchElementException e) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST,
                        "CIFTI average service must be called with zero or one project labels");
                return;
            }
            final XnatProjectdata project = XnatProjectdata.getProjectByIDorAlias(projectID, getUser(), false);
            if (null == project) {
                this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, "No project " + projectID + " found");
                return;
            }
            final Map<String,XnatResourcecatalog> resourceCatalogs = Maps.newHashMap();
            final Map<String,CatCatalogBean> catalogs = Maps.newHashMap();
            getCatalogsMap(project, resourceCatalogs, catalogs);
            for (final String item : getParamValues(RESOURCE_KEY)) {
                final String[] pair = item.split(":");
                if (2 != pair.length) {
                    this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, "invalid resource request " + item);
                    return;
                }
                final XnatResourcecatalog rc = resourceCatalogs.get(pair[0]);
                final File catalogXML = new File(rc.getUri());
                final String resourcePath = catalogXML.getParent();
                final CatCatalogBean catalog = catalogs.get(pair[0]);
                if (null == rc || null == catalog) {
                    this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, projectID + " resource " + pair[0] + " not found");
                    return;
                }
                final String name = pair[1] + suffixes.get(type);
                final CatEntryI entry = CatalogUtils.getEntryByName(catalog, name);
                if (null == entry) {
                    this.getResponse().setStatus(Status.CLIENT_ERROR_NOT_FOUND, project + " resource " + pair[0] + "/files/" + name + " not found in catalog");
                    return;
                }
                final File f = CatalogUtils.getFile(entry, resourcePath);
                if (logger.isTraceEnabled()) {
                    logger.trace("{} {} {} {} -> {}", new Object[]{projectID, pair[0], pair[1], type, f});
                }
                files.add(f);
            }
        }

        if (files.isEmpty()) {
            logger.info("search {} did not produce any CIFTI files", params.get(SEARCH_ID_KEY));
            this.getResponse().setStatus(Status.SUCCESS_NO_CONTENT);
            return;
        }

        final List<String> args = Lists.newArrayList();
        try {
            mapArguments(args, params);
        } catch (IllegalArgumentException e) {
            logger.error("error parsing service argument", e);
            this.getResponse().setStatus(Status.CLIENT_ERROR_BAD_REQUEST, e.getMessage());
            return;
        }

        final MediaType mediaType;
        File output = null;
        try {
            if (params.containsKey("metadata")) {
                output = File.createTempFile("cifti-metadata", "txt");
                args.add("-output-text-file");
                args.add(output.getPath());
                args.add("-select-metadata");
                args.add(files.get(0).getPath());
                mediaType = CIFTI_METADATA;
            } else {
                output = File.createTempFile("cifti-average", "");
                args.add("-output-binary-file");
                args.add(output.getPath());
                args.add("-share-metadata-from-first-cifti-file");
                for (final File f : files) {
                    args.add(f.getPath());
                }
                mediaType = CIFTI_AVERAGE;
            }
        } catch (IOException e) {
            logger.error("unable to create temp file", e);
            this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, e.getMessage());
            return;
        }

        try {
            final StringBuilder message = new StringBuilder();
            final int status = CIFTIAverageServiceManager.run(args, output, message);
            if (0 != status) {
                logger.error("CIFTI averaging failed: error code {}", status);
                this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL,
                        "CIFTI averaging failed, error code " + status + ": " + message);
            }
            this.getResponse().setEntity(new FileRepresentation(output, mediaType, 0));
        } catch (IOException e) {
            logger.error("error running averaging process", e);
            this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, "error computing CIFTI average");
            return;
        } catch (InterruptedException e) {
            logger.error("CIFTI averaging interrupted", e);
            this.getResponse().setStatus(Status.SERVER_ERROR_INTERNAL, "CIFTI averaging interrupted: " + e.getMessage());
            return;
        }
    }


    private static final List<String> mapArguments(final List<String> args, final Multimap<String,Object> params) {
        for (final String key : params.keySet()) {
            final ParameterHandler handler = paramHandlers.get(key);
            if (null != handler) {
                handler.unpack(key, params.get(key), args);
            } else {
                LoggerFactory.getLogger(CIFTIAverage.class).warn("no parameter handler defined for {}", key);
            }
        }
        return args;
    }

    private final ConnectomeType getType() {
        final Iterable<?> types = params.get(CONN_TYPE_KEY);
        if (null == types) {
            throw new IllegalArgumentException("missing required parameter \"type\"");
        } else {
            return ConnectomeType.valueOf(Iterables.getOnlyElement(types).toString());
        }
    }

    private final List<File> getCIFTIFiles(final UserI user, final Iterable<?> searchIDs, final ConnectomeType type)
            throws Exception {
        final List<File> files = Lists.newArrayList();
        for (final Object searchID : searchIDs) {
            logger.trace("using search {} for CIFTI average", searchID);
            final XdatStoredSearch stored = XdatStoredSearch.getXdatStoredSearchsById(searchID, user, true);
            final String rootName = stored.getRootElementName();
            if ("xnat:mrSessionData".equalsIgnoreCase(rootName)) {
                addCIFTIFilesForMRSessions(files, user, stored.getDisplaySearch(user), type);
            } else if ("xnat:subjectData".equalsIgnoreCase(rootName)) {
                addCIFTIFilesForSubjects(files, user, stored.getDisplaySearch(user), type);
            } else {
                logger.debug("ignoring unimplemented search root " + stored.getRootElementName());
            }
        }
        return files;
    }

    private final <T extends Collection<? super File>>
    T addCIFTIFilesForSubjects(final T files, final UserI user, final DisplaySearch subjectSearch, final ConnectomeType type)
            throws Exception {
        subjectSearch.setFields(new DisplayFieldWrapperCollection());
        subjectSearch.addDisplayField("xnat:subjectData", "SUBJECT_ID");
        final XFTTableI table = subjectSearch.execute(user.getLogin());
        if (null == table) {
            logger.info("subject data search {} returned null result", subjectSearch);
            return files;
        }
        table.resetRowCursor();
        final DisplaySearch sessions = new DisplaySearch();
        sessions.setUser(user);
        sessions.setRootElement(XnatMrsessiondata.SCHEMA_ELEMENT_NAME);
        sessions.addDisplayField("xnat:mrSessionData", "PROJECT");
        sessions.addDisplayField("xnat:mrSessionData", "LABEL");

        final CriteriaCollection cc= new CriteriaCollection("OR");
        while (table.hasMoreRows()) {
            final Map<?,?> row = table.nextRowHash();
            logger.trace("subject search found {}", row);
            cc.addClause("xnat:mrSessionData/subject_ID", row.get("subject_id").toString());
        }
        sessions.addCriteria(cc);

        final XFTTableI st = sessions.execute(user.getLogin());
        if (null == st) {
            logger.info("subject sessions search {} returned null result", sessions);
            return files;
        }
        st.resetRowCursor();
        while (st.hasMoreRows()) {
            final Map<?,?> row = st.nextRowHash();
            logger.trace("session search found {}", row);
            addCIFTIFiles(files, row.get("project").toString(), row.get("label").toString(), type);
        }
        return files;
    }

    private final <T extends Collection<? super File>>
    T addCIFTIFilesForMRSessions(final T files, final UserI user,
            final DisplaySearch mrSessionSearch, final ConnectomeType type)
                    throws Exception {
        mrSessionSearch.setFields(new DisplayFieldWrapperCollection());
        mrSessionSearch.addDisplayField("xnat:mrSessionData", "PROJECT");
        mrSessionSearch.addDisplayField("xnat:mrSessionData", "LABEL");
        final XFTTableI table = mrSessionSearch.execute(user.getLogin());
        if (null == table) {
            logger.info("sessions search {} returned null result", mrSessionSearch);
            return files;
        }
        table.resetRowCursor();
        while (table.hasMoreRows()) {
            final Map<?,?> row = table.nextRowHash();
            logger.trace("session search found {}", row);
            addCIFTIFiles(files, row.get("project").toString(), row.get("label").toString(), type);
        }
        return files;
    }

    private final <T extends Collection<? super File>>
    T addCIFTIFiles(final T files, final String project, final String session, final ConnectomeType type) {
        final File rootArchivePath = new File(ArcSpecManager.GetInstance().getGlobalArchivePath());
        final File projdir = new File(rootArchivePath, project);
        final File arc001 = new File(projdir, "arc001");
        final File sessdir = new File(arc001, session);
        addCIFTIFiles(files, sessdir, type);
        return files;
    }

    private final <T extends Collection<? super File>>
    T addCIFTIFiles(final T files, final File sessionRoot, final ConnectomeType type) {
        final File resultsDir = CIFTIAverageServiceManager.getConnectomeDir(sessionRoot);
        final File ciftiFile = new File(resultsDir, singleSubjectFileNames.get(type) + suffixes.get(type));
        if (ciftiFile.canRead()) {
            files.add(ciftiFile);
        }
        return files;
    }
}
