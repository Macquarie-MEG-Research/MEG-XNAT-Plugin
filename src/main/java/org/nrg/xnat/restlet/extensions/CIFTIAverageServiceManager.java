/**
 * Copyright (c) 2013 Washington University School of Medicine
 */
package org.nrg.xnat.restlet.extensions;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.nrg.hcp.cifti.views.StreamConsumer;
import org.nrg.xdat.XDAT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Bridge the gap between the XNAT Restlet service and the Spring configuration
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class CIFTIAverageServiceManager {
    private static CIFTIAverageServiceManager instance;

    /**
     * Get the session-directory relative path of the directory containing NIfTI-formatted connectome files
     * @return directory path
     */
    public static File getConnectomeDir(final File sessionRoot) {
        return new File(sessionRoot, getInstance().connectome_nii_path);
    }

    public static CIFTIAverageServiceManager getInstance() {
        if (null == instance) {
            instance = XDAT.getContextService().getBean(CIFTIAverageServiceManager.class);
            if (null == instance) {
                LoggerFactory.getLogger(CIFTIAverageServiceManager.class).error("ERROR: CIFTI average service configuration not properly initialized");
                throw new IllegalStateException("CIFTI average service configuration not provided");
            }
        }
        return instance;
    }

    /**
     * Run the CIFTI average command
     * @param args command-line arguments for caret6 (DO NOT include -cifti-average, -number-of-threads)
     * @param output File to which results will be directed
     * @param message Appendable (e.g., StringBuilder) to collect stderr output
     * @return status code; if nonzero, contents of stderr will be appended to message
     * @throws IOException
     * @throws InterruptedException
     */
    public static int run(final Iterable<String> args, final File output, final Appendable message)
            throws IOException,InterruptedException {
        return getInstance().doRun(args, output, message);
    }

    private final Logger logger = LoggerFactory.getLogger(CIFTIAverageServiceManager.class);

    private String caret6_command, connectome_nii_path;
    private int n_threads;

    private int doRun(final Iterable<String> args, final File output, final Appendable message)
            throws IOException,InterruptedException {
        final List<String> command = Lists.newArrayList(caret6_command);
        command.add("-cifti-average");
        command.add("-number-of-threads");
        command.add(Integer.toString(n_threads));
        Iterables.addAll(command, args);
        final Process process = Runtime.getRuntime().exec(Iterables.toArray(command, String.class));
        final StreamConsumer stdout = new StreamConsumer(process.getInputStream()),
                stderr = new StreamConsumer(process.getErrorStream());
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("executing caret6 as {} with stdout, stderr consumers {} {}",
                        new Object[]{command, stdout, stderr});
            }
            final int status = process.waitFor();
            if (0 != status) {
                message.append(stderr.toString());
            }
            return status;
        } finally {
            stdout.close();
            stderr.close();
        }
    }

    public void setCaret6_command(final String s) { this.caret6_command = s; }

    public void setConnectome_nii_path(final String s) { this.connectome_nii_path = s; }

    public void setN_threads(final int n) { this.n_threads = n; }
}
