package com.fooholdings.fdp.core.ingestion;

import com.fooholdings.fdp.core.persistence.IngestionRunEntity;
import com.fooholdings.fdp.core.persistence.IngestionRunRepository;
import com.fooholdings.fdp.core.source.SourceSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of fdp_core.ingestion_run records.
 *
 * Responsibilities:
 *   startRun  — creates a RUNNING row with scope metadata and lock correlation
 *   finishSuccess — marks SUCCESS with a summary message
 *   finishFailure — marks FAILED with truncated exception message
 *
 * Every ingestion flow (lock -> run -> work -> finish -> unlock) passes through
 * this service. The run ID is threaded through to raw_payload and
 * price_observation for full audit traceability.
 *
 */
@Service
public class IngestionRunService {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunService.class);
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final IngestionRunRepository repo;
    private final SourceSystemService sourceSystemService;
    private final ObjectMapper mapper;

    public IngestionRunService(IngestionRunRepository repo,
                               SourceSystemService sourceSystemService,
                               ObjectMapper mapper) {
        this.repo = repo;
        this.sourceSystemService = sourceSystemService;
        this.mapper = mapper;
    }

     /**
     * Creates and persists a RUNNING ingestion run record.
     *
     * @param sourceCode
     * @param scope       key/value metadata describing the ingestion scope
     * @param lockedBy    identifier of the locking agent, recorded for correlation
     * @return the new run's UUID
     */

    public UUID startRun(String sourceCode, Map<String, Object> scope, String lockedBy) {
        short sourceId = sourceSystemService.getRequiredIdByCode(sourceCode);

        IngestionRunEntity run = new IngestionRunEntity();
        run.setId(UUID.randomUUID());
        run.setSourceSystemId(sourceId);
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setLockedAt(Instant.now());
        run.setLockedBy(lockedBy);
        run.setRequestedScopeJson(serializeScope(scope));

        repo.save(run);
        log.info("[run:{}] Started — source={}, lockedBy={}", run.getId(), sourceCode, lockedBy);
        return run.getId();
    }

    //Marks an existing run as SUCCESS and logs the result summary.
    public void finishSuccess(UUID runId, String message) {
        IngestionRunEntity run = repo.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
        run.setFinishedAt(Instant.now());
        run.setStatus("SUCCESS");
        run.setMessage(truncate(message));
        repo.save(run);
        log.info("[run:{}] SUCCESS — {}", runId, message);
    }

    //Marks an existing run as FAILED so the run is never left in RUNNING state indefinitely.
    public void finishFailure(UUID runId, Throwable ex) {
        try {
            IngestionRunEntity run = repo.findById(runId)
                    .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
            run.setFinishedAt(Instant.now());
            run.setStatus("FAILED");
            run.setMessage(truncate(ex.getClass().getSimpleName() + ": " +
                    (ex.getMessage() != null ? ex.getMessage() : "(no message)")));
            repo.save(run);
            log.error("[run:{}] FAILED — {}: {}", runId, ex.getClass().getSimpleName(), ex.getMessage());
        } catch (Exception saveEx) {
            // Do not mask the original exception
            log.error("[run:{}] Could not persist FAILED status: {}", runId, saveEx.getMessage());
        }
    }

    // Helpers

    private String serializeScope(Map<String, Object> scope) {
        if (scope == null || scope.isEmpty()) return null;
        try {
            return mapper.writeValueAsString(scope);
        } catch (Exception e) {
            log.warn("Could not serialize ingestion scope to JSON: {}", e.getMessage());
            return "{\"_error\":\"scope_serialization_failed\"}";
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_MESSAGE_LENGTH ? s.substring(0, MAX_MESSAGE_LENGTH) : s;
    }
}
