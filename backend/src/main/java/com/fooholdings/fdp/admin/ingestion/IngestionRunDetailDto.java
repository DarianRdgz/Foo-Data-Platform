package com.fooholdings.fdp.admin.ingestion;

import java.time.Instant;
import java.util.UUID;

public record IngestionRunDetailDto(
        UUID id,
        String source,
        String status,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int recordsWritten,
        String message,
        String requestedScopeJson,
        String lockedBy,
        Instant lockedAt,
        String errorDetail
) { }