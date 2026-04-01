package com.fooholdings.fdp.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response body for GET /api/area/{geoLevel}/{geoId}.
 *
 * Returns geo metadata and one snapshot per category (the latest).
 * History, children, and map tiles are deferred to Story 5.5.
 */
public record AreaResponse(
        UUID geoId,
        String geoLevel,
        String name,
        String displayLabel,
        String fipsCode,
        String cbsaCode,
        String zipCode,
        List<AreaSnapshotView> snapshots
) {}