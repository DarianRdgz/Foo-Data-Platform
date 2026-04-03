package com.fooholdings.fdp.api.dto;

import java.util.UUID;

public record GeoParentView(
        UUID geoId,
        String geoLevel,
        String name,
        String displayLabel
) {}