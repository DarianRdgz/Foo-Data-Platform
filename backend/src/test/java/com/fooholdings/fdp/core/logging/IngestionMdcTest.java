package com.fooholdings.fdp.core.logging;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class IngestionMdcTest {

    @Test
    void closesAndRestoresPreviousValues() {
        MDC.put(IngestionMdc.KEY_RUN_ID, "prior");
        MDC.put(IngestionMdc.KEY_SOURCE, "PRIOR_SOURCE");

        UUID runId = UUID.randomUUID();
        try (@SuppressWarnings("unused") var ignored = IngestionMdc.withRun(runId, "KROGER")) {
            assertEquals(runId.toString(), MDC.get(IngestionMdc.KEY_RUN_ID));
            assertEquals("KROGER", MDC.get(IngestionMdc.KEY_SOURCE));
        }

        assertEquals("prior", MDC.get(IngestionMdc.KEY_RUN_ID));
        assertEquals("PRIOR_SOURCE", MDC.get(IngestionMdc.KEY_SOURCE));
        MDC.clear();
    }

    @Test
    void clearsValuesWhenNoPrior() {
        MDC.remove(IngestionMdc.KEY_RUN_ID);
        MDC.remove(IngestionMdc.KEY_SOURCE);

        UUID runId = UUID.randomUUID();
        try (@SuppressWarnings("unused") var ignored = IngestionMdc.withRun(runId, "KROGER")) {
            assertNotNull(MDC.get(IngestionMdc.KEY_RUN_ID));
        }

        assertNull(MDC.get(IngestionMdc.KEY_RUN_ID));
        assertNull(MDC.get(IngestionMdc.KEY_SOURCE));
        MDC.clear();
    }
}