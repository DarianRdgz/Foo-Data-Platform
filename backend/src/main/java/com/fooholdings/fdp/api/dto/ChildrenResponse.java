package com.fooholdings.fdp.api.dto;

import java.util.List;
import java.util.UUID;

public record ChildrenResponse(
        UUID geoId,
        String geoLevel,
        String childGeoLevel,
        List<ChildAreaView> children
) {}
