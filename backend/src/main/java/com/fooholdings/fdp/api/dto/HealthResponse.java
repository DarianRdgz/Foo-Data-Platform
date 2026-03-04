package com.fooholdings.fdp.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HealthResponse(
        Instant generatedAt,
        List<SourceIngestionStatus> sources
) {
    public record SourceIngestionStatus(
            String source,
            UUID lastRunId,
            String status,
            Instant startedAt,
            Instant finishedAt,
            String message
    ) {}
}