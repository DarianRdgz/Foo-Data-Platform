package com.fooholdings.fdp.api.dto;

import java.util.List;
import java.util.Map;

public record GeoJsonFeatureCollection(
        String type,
        List<Feature> features
) {
    public record Feature(
            String type,
            Object geometry,
            Map<String, Object> properties
    ) {}
}
