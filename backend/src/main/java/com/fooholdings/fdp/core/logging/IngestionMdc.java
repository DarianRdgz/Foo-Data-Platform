package com.fooholdings.fdp.core.logging;

import java.util.UUID;

import org.slf4j.MDC;

public final class IngestionMdc implements AutoCloseable {
    public static final String KEY_RUN_ID = "ingestion_run_id";
    public static final String KEY_SOURCE = "source";

    private final String priorRunId = MDC.get(KEY_RUN_ID);
    private final String priorSource = MDC.get(KEY_SOURCE);

    private IngestionMdc(UUID runId, String source) {
        if (runId != null) MDC.put(KEY_RUN_ID, runId.toString());
        if (source != null && !source.isBlank()) MDC.put(KEY_SOURCE, source);
    }

    public static IngestionMdc withRun(UUID runId, String source) {
        return new IngestionMdc(runId, source);
    }

    @Override
    public void close() {
        restore(KEY_RUN_ID, priorRunId);
        restore(KEY_SOURCE, priorSource);
    }

    private static void restore(String key, String value) {
        if (value == null) MDC.remove(key);
        else MDC.put(key, value);
    }
}