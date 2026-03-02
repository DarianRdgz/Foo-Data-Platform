package com.fooholdings.fdp.api.dto;

import java.time.Instant;
import java.util.UUID;

// Stable API DTO for store location reads.
public record LocationResponse(
        UUID id,
        String source,
        String sourceLocationId,
        String chainCode,
        String name,
        String phone,
        String addressLine1,
        String city,
        String stateCode,
        String postalCode,
        String countryCode,
        Long geoRegionId,
        Instant firstSeenAt,
        Instant lastSeenAt
) {}