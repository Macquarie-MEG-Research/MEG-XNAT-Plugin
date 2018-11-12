/**
 * Copyright (c) 2013 Washington University School of Medicine
 */
package org.nrg.hcp.cifti.views;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.View;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class CIFTIMetadataXMLView implements View {
    private final Logger logger = LoggerFactory.getLogger(CIFTIMetadataXMLView.class);
    private final String wb_command;
    
    public CIFTIMetadataXMLView(final String wb_command) {
        this.wb_command = wb_command;
    }
    

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.View#getContentType()
     */
    @Override
    public String getContentType() {
        return "application/x-cifti-metadata+xml";
    }

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.View#render(java.util.Map, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void render(final Map<String,?> model,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, InterruptedException {
        final List<String> command = Lists.newArrayList(wb_command);
        command.add("-nifti-information");
        command.add(((File)model.get("file")).getPath());
        command.add("-print-xml");
        final StringBuilder stderr = new StringBuilder();
        final ServletOutputStream out = response.getOutputStream();
        try {
            final int status = run(command, out, stderr);
            if (0 != status) {
                response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), stderr.toString());
                return;
            }
        } finally {
            out.close();
        }
    }
    
    private int run(final Iterable<String> command, final OutputStream stdout, final Appendable strerr)
            throws IOException,InterruptedException {
        final Process process = Runtime.getRuntime().exec(Iterables.toArray(command, String.class));
        final StreamConsumer stderr = new StreamConsumer(process.getErrorStream());
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("executing command as {} with stdout, stderr consumers {} {}",
                        new Object[]{command, stdout, stderr});
            }
            stderr.start();
            try {
                ByteStreams.copy(process.getInputStream(), stdout);
            } finally {
                stdout.close();
            }
            final int status = process.waitFor();
            if (null != strerr) {
                stderr.appendTo(strerr);
            }
            return status;
        } finally {
            stderr.close();
        }
    }
}
