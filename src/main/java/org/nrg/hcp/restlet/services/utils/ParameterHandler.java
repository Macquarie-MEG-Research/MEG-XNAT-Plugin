/**
 * Copyright (c) 2011 Washington University
 */
package org.nrg.hcp.restlet.services.utils;

import java.util.List;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public interface ParameterHandler {
    List<String> unpack(final String key, final Iterable<?> values, final List<String> args);
}
