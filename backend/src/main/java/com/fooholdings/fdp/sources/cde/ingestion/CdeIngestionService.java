package com.fooholdings.fdp.sources.cde.ingestion;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;

@Service
public class CdeIngestionService {

    private static final String SOURCE         = "CDE";
    private static final String INGESTION_TYPE = "STATE_CRIME";
    private static final Duration LOCK_TTL     = Duration.ofMinutes(20);

    private final IngestionLockService lockService;
    private final IngestionRunService  runService;
    private final CdeAdapter           adapter;

    public CdeIngestionService(IngestionLockService lockService,
                               IngestionRunService runService,
                               CdeAdapter adapter) {
        this.lockService = lockService;
        this.runService  = runService;
        this.adapter     = adapter;
    }

    public String ingest(String lockedBy) {
        boolean acquired = lockService.tryAcquire(SOURCE, lockedBy, LOCK_TTL);
        if (!acquired) {
            throw new IngestionLockException("Lock already held for " + SOURCE + " " + INGESTION_TYPE);
        }

        UUID runId = runService.startRun(SOURCE, Map.of("type", INGESTION_TYPE), lockedBy);
        try {
            int records = adapter.ingest();
            String msg = "CDE crime ingest complete: " + records + " rows written";
            runService.finishSuccess(runId, records, msg);
            return msg;
        } catch (Exception e) {
            runService.finishFailure(runId, e);
            throw e;
        } finally {
            lockService.release(SOURCE, lockedBy);
        }
    }
}