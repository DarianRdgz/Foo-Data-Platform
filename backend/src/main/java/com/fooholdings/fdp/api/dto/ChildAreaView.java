package com.fooholdings.fdp.api.dto;

import java.util.UUID;

public record ChildAreaView(
        UUID geoId,
        String geoLevel,
        String name,
        String displayLabel,
        long coverageCount,
        String fipsCode,
        String cbsaCode,
        String zipCode
) {}
