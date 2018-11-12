/**
 * Copyright (c) 2011 Washington University
 */
package org.nrg.hcp.restlet.services.utils;

import java.util.List;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class IgnoreParameterHandler implements ParameterHandler {
    /* (non-Javadoc)
     * @see org.nrg.hcp.restlet.services.ParameterHandler#unpack(java.lang.String, java.lang.Iterable, java.util.List)
     */
    @Override
    public List<String> unpack(final String key, final Iterable<?> values, final List<String> args) {
        return args;
    }
}
