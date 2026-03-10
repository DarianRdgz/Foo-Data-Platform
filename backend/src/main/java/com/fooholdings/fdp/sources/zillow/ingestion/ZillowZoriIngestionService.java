package com.fooholdings.fdp.sources.zillow.ingestion;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import org.springframework.stereotype.Service;

@Service
public class ZillowZoriIngestionService {

    private static final String SOURCE = "ZILLOW";
    private static final String INGESTION_TYPE = "ZORI";
    private static final Duration LOCK_TTL = Duration.ofMinutes(20);

    private final IngestionLockService lockService;
    private final IngestionRunService runService;
    private final ZillowZoriAdapter adapter;

    public ZillowZoriIngestionService(
            IngestionLockService lockService,
            IngestionRunService runService,
            ZillowZoriAdapter adapter
    ) {
        this.lockService = lockService;
        this.runService = runService;
        this.adapter = adapter;
    }

    public String ingest(String lockedBy) {
        boolean acquired = lockService.tryAcquire(SOURCE, lockedBy, LOCK_TTL);
        if (!acquired) {
            throw new IngestionLockException("Lock already held for " + SOURCE + " " + INGESTION_TYPE + " ingestion");
        }

        UUID runId = runService.startRun(SOURCE, Map.of("type", INGESTION_TYPE), lockedBy);

        try {
            int records = adapter.ingest();
            runService.finishSuccess(runId, records, "Zillow ZORI ingest complete: " + records + " rows written");
            return "Zillow ZORI ingest complete: " + records + " rows written";
        } catch (Exception e) {
            runService.finishFailure(runId, e);
            throw e;
        } finally {
            lockService.release(SOURCE, lockedBy);
        }
    }
}
