package com.fooholdings.fdp.api.dto;

public record AreaSearchResult(
        String geoId,
        String geoLevel,
        String name,
        String displayLabel,
        String fipsCode,
        String cbsaCode,
        String zipCode
) {}
