package com.fooholdings.fdp.api.service;

import java.util.List;
import java.util.Locale;

final class GeoLevelValidator {

    private static final List<String> SUPPORTED_LEVELS = List.of(
            "national",
            "state",
            "metro",
            "county",
            "city",
            "zip"
    );

    private static final List<String> SUPPORTED_MAP_LEVELS = List.of(
            "state",
            "metro",
            "county",
            "city",
            "zip"
    );

    private GeoLevelValidator() {
    }

    static String requireSupported(String geoLevel) {
        if (geoLevel == null || geoLevel.isBlank()) {
            throw new IllegalArgumentException("geoLevel is required");
        }

        String normalized = geoLevel.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "geoLevel must be one of " + String.join(", ", SUPPORTED_LEVELS)
            );
        }
        return normalized;
    }

    static String requireSupportedMapLevel(String geoLevel) {
        if (geoLevel == null || geoLevel.isBlank()) {
            throw new IllegalArgumentException("geoLevel is required");
        }

        String normalized = geoLevel.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_MAP_LEVELS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "map geoLevel must be one of " + String.join(", ", SUPPORTED_MAP_LEVELS)
            );
        }
        return normalized;
    }
}
