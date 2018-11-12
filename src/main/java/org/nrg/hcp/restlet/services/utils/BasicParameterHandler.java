/**
 * Copyright (c) 2011 Washington University
 */
package org.nrg.hcp.restlet.services.utils;

import java.util.Arrays;
import java.util.List;

/**
 * @author Kevin A. Archie <karchie@wustl.edu>
 *
 */
public class BasicParameterHandler implements ParameterHandler {
    private final String flag;
    private final String separator;
    private final int nArgs;
    
    public BasicParameterHandler(final String flag, final String separator, final int nArgs) {
        this.flag = flag;
        this.separator = separator;
        this.nArgs = nArgs;
    }
    
    public BasicParameterHandler(final String flag) {
        this(flag, null, 1);
    }
    
    @Override
    public List<String> unpack(final String key, final Iterable<?> values, final List<String> args) {
        for (final Object value : values) {
            if (null == value) {
                throw new IllegalArgumentException("null value for " + key);
            }
            args.add(flag);
            if (null != separator) {
                assert null != value;
                final String[] vs = value.toString().split(separator);
                if (nArgs != vs.length) {
                    throw new IllegalArgumentException(flag + " expects " + nArgs +
                            ", got " + vs.length + ": " + Arrays.toString(vs));
                }
                for (final String v : vs) {
                    args.add(v);
                }
            } else {
                assert null != value;
                args.add(value.toString());
            }
        }
        return args;
    }
}
