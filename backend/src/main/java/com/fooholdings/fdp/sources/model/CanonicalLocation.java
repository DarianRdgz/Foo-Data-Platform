package com.fooholdings.fdp.sources.model;

/**
 * Canonical representation of an upstream store/location.
 */
public record CanonicalLocation(
        String sourceLocationId,
        String chainCode,
        String name,
        String phone,
        String addressLine1,
        String city,
        String stateCode,
        String postalCode,
        String countryCode
) { }