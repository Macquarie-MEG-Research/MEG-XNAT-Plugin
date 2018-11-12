/**
 * Copyright (c) 2013 Washington University School of Medicine
 */
package org.nrg.hcp.cifti.views;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

/**
 * 
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public final class StreamConsumer implements Runnable,Closeable {
    private final Logger logger = LoggerFactory.getLogger(StreamConsumer.class);
    private final InputStream in;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public StreamConsumer(final InputStream in) {
        this.in = in;
    }

    /*
     * (non-Javadoc)
     * @see java.io.Closeable#close()
     */
    public void close() throws IOException {
        in.close();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Runnable#run()
     */
    public void run() {
        try {
            IOException ioexception = null;
            try {
                ByteStreams.copy(in, out);
            } catch (IOException e) {
                throw ioexception = e;
            } finally {
                try {
                    in.close();
                } catch (IOException e) {
                    if (null == ioexception) {
                        throw e;
                    } else {
                        logger.error("error closing stream", e);
                        throw ioexception;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("error consuming stream", e);
        }
    }

    public StreamConsumer start() {
        new Thread(this).start();
        return this;
    }

    public <T extends Appendable> T appendTo(final T a) throws IOException {
        a.append(out.toString());
        return a;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return out.toString();
    }

}