package com.fooholdings.fdp.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Stable API DTO for individual price observations.
public record PriceResponse(
        UUID sourceProductId,
        UUID storeLocationId,
        String source,
        Instant observedAt,
        String currencyCode,
        BigDecimal price,
        BigDecimal regularPrice,
        BigDecimal promoPrice,
        Boolean isOnSale
) {}