package com.fooholdings.fdp.core.logging;

import org.slf4j.MDC;

public final class MdcScope implements AutoCloseable {
    private final String key;
    private final String prior;

    private MdcScope(String key, String value) {
        this.key = key;
        this.prior = MDC.get(key);
        if (value == null) MDC.remove(key);
        else MDC.put(key, value);
    }

    public static MdcScope with(String key, String value) {
        return new MdcScope(key, value);
    }

    @Override
    public void close() {
        if (prior == null) MDC.remove(key);
        else MDC.put(key, prior);
    }
}