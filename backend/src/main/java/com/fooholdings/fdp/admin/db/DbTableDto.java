package com.fooholdings.fdp.admin.db;

import java.time.Instant;

public record DbTableDto(
        String schemaName,
        String tableName,
        long rowCountEstimate,
        long totalBytes,
        String totalSizePretty,
        Instant lastVacuum,
        Instant lastAutovacuum,
        Instant lastAnalyze,
        Instant lastAutoanalyze
) { }