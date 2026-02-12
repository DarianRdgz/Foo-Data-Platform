package com.fooholdings.fdp.ingestion;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestionRunService {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunService.class);

    private final IngestionRunRepository repo;

    public IngestionRunService(IngestionRunRepository repo) {
        this.repo = repo;
    }

    public Long startRun(String source, String message) {
        IngestionRunEntity run = new IngestionRunEntity();
        run.setSource(source);
        run.setStartedAt(Instant.now());
        run.setStatus("RUNNING");
        run.setMessage(message);

        IngestionRunEntity saved = repo.save(run);

        log.info("Ingestion run started: id={}, source={}", saved.getId(), source);
        return saved.getId();
    }

    public void endSuccess(Long runId, String message) {
        IngestionRunEntity run = repo.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setStatus("SUCCESS");
        run.setMessage(message);
        repo.save(run);

        log.info("Ingestion run finished: id={}, status=SUCCESS", runId);
    }

    public void endFailure(Long runId, Throwable ex) {
        IngestionRunEntity run = repo.findById(runId).orElseThrow();
        run.setFinishedAt(Instant.now());
        run.setStatus("FAILED");

        // Keep message short + safe (no secrets)
        String msg = ex.getClass().getSimpleName() + ": " + (ex.getMessage() == null ? "" : ex.getMessage());
        if (msg.length() > 500) msg = msg.substring(0, 500);
        run.setMessage(msg);

        repo.save(run);

        log.error("Ingestion run finished: id={}, status=FAILED, error={}", runId, msg);
    }
}
