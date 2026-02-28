package com.fooholdings.fdp.sources.model;

/**
 * Convenience DTO: product + current price observation in a given location.
 */
public record CanonicalProductPrice(
        String sourceLocationId,
        CanonicalProduct product,
        CanonicalPriceObservation priceObservation
) { }