package com.fooholdings.fdp.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StapleZipBasketResponse(
        String stateCode,
        String zipCode,
        Instant fromInclusive,
        Instant toExclusive,
        BigDecimal basketAvgPrice,
        long totalSamples,
        List<StapleItemAvg> items
) {
    public record StapleItemAvg(
            String upc,
            String name,
            List<String> categories,
            BigDecimal avgPrice,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            long sampleSize
    ) {}
}