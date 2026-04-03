package com.fooholdings.fdp.sources.cde.ingestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.core.ingestion.SourceArtifactJdbcRepository;
import com.fooholdings.fdp.geo.event.AreaIngestCompletedEvent;
import com.fooholdings.fdp.sources.cde.artifact.CdeArtifact;
import com.fooholdings.fdp.sources.cde.artifact.CdeArtifactSource;

@Service
public class CdeIngestionService {

    private static final String SOURCE         = "CDE";
    private static final String INGESTION_TYPE = "STATE_CRIME";
    private static final Duration LOCK_TTL     = Duration.ofMinutes(20);

    private final IngestionLockService lockService;
    private final IngestionRunService runService;
    private final SourceArtifactJdbcRepository artifactRepo;
    private final CdeArtifactSource artifactSource;
    private final CdeAdapter adapter;
    private final ApplicationEventPublisher eventPublisher;

    public CdeIngestionService(IngestionLockService lockService,
                               IngestionRunService runService,
                               SourceArtifactJdbcRepository artifactRepo,
                               CdeArtifactSource artifactSource,
                               CdeAdapter adapter,
                               ApplicationEventPublisher eventPublisher) {
        this.lockService = lockService;
        this.runService = runService;
        this.artifactRepo = artifactRepo;
        this.artifactSource = artifactSource;
        this.adapter = adapter;
        this.eventPublisher = eventPublisher;
    }

    public String ingest(String lockedBy) {
        boolean acquired = lockService.tryAcquire(SOURCE, lockedBy, LOCK_TTL);
        if (!acquired) {
            throw new IngestionLockException("Lock already held for " + SOURCE + " " + INGESTION_TYPE);
        }

        UUID runId = runService.startRun(SOURCE, Map.of("type", INGESTION_TYPE), lockedBy);

        try {
            List<CdeArtifact> artifacts = artifactSource.discoverPendingArtifacts();
            int recordsWritten = 0;
            List<String> failures = new ArrayList<>();

            for (CdeArtifact artifact : artifacts) {
                try {
                    recordsWritten += adapter.ingestArtifact(artifact);
                    artifactRepo.markIngested(artifact.artifactId(), runId);
                } catch (RuntimeException artifactError) {
                    artifactRepo.markFailed(artifact.artifactId(), runId, artifactError.getMessage());
                    failures.add(artifact.originalFilename() + ": " + artifactError.getMessage());
                }
            }

            if (!failures.isEmpty()) {
                throw new IllegalStateException(
                        "CDE ingest finished with " + failures.size() + " failed artifact(s). See fdp_core.source_artifact.notes.");
            }

            if (recordsWritten > 0) {
                eventPublisher.publishEvent(new AreaIngestCompletedEvent(this, SOURCE, null, recordsWritten));
            }

            String msg = "CDE crime ingest complete: " + recordsWritten
                    + " rows written from " + artifacts.size() + " artifact(s)";
            runService.finishSuccess(runId, recordsWritten, msg);
            return msg;
        } catch (RuntimeException e) {
            runService.finishFailure(runId, e);
            throw e;
        } finally {
            lockService.release(SOURCE, lockedBy);
        }
    }
}
