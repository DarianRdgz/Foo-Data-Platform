package com.fooholdings.fdp.sources.fema.ingestion;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.geo.service.DisasterRiskScoreService;

@Service
public class FemaDisasterIngestionService {

    private static final String SOURCE         = "FEMA";
    private static final String INGESTION_TYPE = "DISASTER_DECLARATIONS";
    private static final Duration LOCK_TTL     = Duration.ofHours(1);

    private final IngestionLockService     lockService;
    private final IngestionRunService      runService;
    private final FemaDisasterAdapter      adapter;
    private final DisasterRiskScoreService riskScoreService;

    public FemaDisasterIngestionService(IngestionLockService lockService,
                                        IngestionRunService runService,
                                        FemaDisasterAdapter adapter,
                                        DisasterRiskScoreService riskScoreService) {
        this.lockService      = lockService;
        this.runService       = runService;
        this.adapter          = adapter;
        this.riskScoreService = riskScoreService;
    }

    public String ingest(String lockedBy) {
        boolean acquired = lockService.tryAcquire(SOURCE, lockedBy, LOCK_TTL);
        if (!acquired) {
            throw new IngestionLockException("Lock already held for " + SOURCE + " " + INGESTION_TYPE);
        }

        UUID runId = runService.startRun(SOURCE, Map.of("type", INGESTION_TYPE), lockedBy);
        try {
            int records = adapter.ingest();
            // Recompute composite scores for all geos that now have FEMA data
            int scored = riskScoreService.recomputeAll();
            String msg = "FEMA disaster ingest complete: " + records + " rows written, "
                         + scored + " composite scores updated";
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