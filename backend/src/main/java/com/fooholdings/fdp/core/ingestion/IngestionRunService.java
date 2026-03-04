package com.fooholdings.fdp.core.ingestion;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.persistence.IngestionRunEntity;
import com.fooholdings.fdp.core.persistence.IngestionRunRepository;
import com.fooholdings.fdp.core.source.SourceSystemService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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
    private static final int ERROR_DETAIL_MAX_CHARS = 8000;

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
    public void finishSuccess(UUID runId, int recordsWritten, String message) {
        IngestionRunEntity run = repo.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));

        run.setFinishedAt(Instant.now());
        run.setStatus("SUCCESS");
        run.setRecordsWritten(Math.max(recordsWritten, 0));
        run.setMessage(truncate(message)); // keep your existing truncate for message
        repo.save(run);
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

            String raw = stackTraceToString(ex);
            run.setErrorDetail(truncateTo(raw, ERROR_DETAIL_MAX_CHARS));

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
        } catch (JacksonException e) {
            log.warn("Could not serialize ingestion scope to JSON: {}", e.getMessage());
            return "{\"_error\":\"scope_serialization_failed\"}";
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > MAX_MESSAGE_LENGTH ? s.substring(0, MAX_MESSAGE_LENGTH) : s;
    }

    private static String truncateTo(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "\n... (truncated)";
    }

    private static String stackTraceToString(Throwable ex) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
