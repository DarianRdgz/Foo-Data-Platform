package com.fooholdings.fdp.admin.ingestion;

import java.time.Instant;
import java.util.UUID;

public record IngestionRunRowDto(
        UUID id,
        String source,
        String status,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int recordsWritten,
        String errorMessage
) { }