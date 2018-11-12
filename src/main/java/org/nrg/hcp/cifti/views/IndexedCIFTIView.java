/**
 * Copyright (c) 2013 Washington University School of Medicine
 */
package org.nrg.hcp.cifti.views;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collections;
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
import com.google.common.io.Files;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public final class IndexedCIFTIView implements View {
    private final Logger logger = LoggerFactory.getLogger(IndexedCIFTIView.class);
    private final String wb_command;

    public IndexedCIFTIView(final String wb_command) {
        this.wb_command = wb_command;
    }

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.View#getContentType()
     */
    public String getContentType() {
        return "application/x-hcp-workbench-cifti-row";
    }

    /* (non-Javadoc)
     * @see org.springframework.web.servlet.View#render(java.util.Map, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void render(final Map<String,?> model,
            final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, InterruptedException {
        final File output = File.createTempFile("indexed-cifti-view", ".row");
        final List<String> command = Lists.newArrayList(wb_command);
        command.add("-backend-average-dense-roi");
        command.add(model.get("row").toString());
        command.add(output.getPath());
        final StringBuilder stderr = new StringBuilder();
        final int status = run(command, Collections.singleton((File)model.get("file")),
                output, response.getOutputStream(), null, stderr);
        if (0 != status) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR.value(), stderr.toString());
            return;
        }
        final ServletOutputStream out = response.getOutputStream();
        try {
            Files.copy(output, out);
        } finally {
            out.close();
        }
    }

    private int run(final Iterable<String> command, final Iterable<File> inputs,
            final File result, final OutputStream dest, 
            final Appendable strout, final Appendable strerr)
                    throws IOException,InterruptedException {
        final Process process = Runtime.getRuntime().exec(Iterables.toArray(command, String.class));
        final PrintWriter stdin = new PrintWriter(process.getOutputStream());
        try {
            for (final File f : inputs) {
                stdin.println(f.getPath());
            }
        } finally {
            stdin.close();
        }
        final StreamConsumer stdout = new StreamConsumer(process.getInputStream()),
                stderr = new StreamConsumer(process.getErrorStream());
        try {
            stdout.start();
            stderr.start();
            if (logger.isTraceEnabled()) {
                logger.trace("executing command as {} with stdout, stderr consumers {} {}",
                        new Object[]{command, stdout, stderr});
            }
            final int status = process.waitFor();
            if (null != strout) {
                stdout.appendTo(strout);
            }
            if (null != strerr) {
                stderr.appendTo(strerr);
            }
            if (0 == status) {
                try {
                    Files.copy(result, dest);
                } finally {
                    dest.close();
                    result.delete();
                }
            }
            return status;
        } finally {
            stdout.close();
            stderr.close();
        }
    }


}
