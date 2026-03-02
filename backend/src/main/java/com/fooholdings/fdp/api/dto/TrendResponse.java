package com.fooholdings.fdp.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Stable API DTO for trend responses.
 *
 * Kept deliberately generic: the controller/service can choose bucket granularity.
 */
public record TrendResponse(
        String period,
        List<TrendPoint> points
) {
    public record TrendPoint(
            Instant bucketStart,
            Instant bucketEnd,
            BigDecimal min,
            BigDecimal max,
            BigDecimal avg,
            long sampleSize
    ) {}
}