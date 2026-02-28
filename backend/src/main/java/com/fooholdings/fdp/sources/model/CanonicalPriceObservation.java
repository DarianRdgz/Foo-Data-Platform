package com.fooholdings.fdp.sources.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical representation of a price observation.
 */
public record CanonicalPriceObservation(
        Instant observedAt,
        String currencyCode,
        BigDecimal price,
        BigDecimal regularPrice,
        BigDecimal promoPrice,
        Boolean isOnSale
) { }