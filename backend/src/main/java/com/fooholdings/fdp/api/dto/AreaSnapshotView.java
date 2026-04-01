package com.fooholdings.fdp.api.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * Represents a single latest area_snapshot row for one category.
 * The payload is kept as a Map<String, Object> to preserve the flexible
 * schema-per-category design. Jackson serializes it directly to JSON.
 */
public record AreaSnapshotView(
        String category,
        LocalDate snapshotPeriod,
        String source,
        boolean isRollup,
        Map<String, Object> payload
) {}