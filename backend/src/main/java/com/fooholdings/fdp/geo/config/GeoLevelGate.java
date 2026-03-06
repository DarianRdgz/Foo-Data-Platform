package com.fooholdings.fdp.geo.config;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class GeoLevelGate {

    private final GeoProperties geoProperties;

    public GeoLevelGate(GeoProperties geoProperties) {
        this.geoProperties = geoProperties;
    }

    public boolean isEnabled(String level) {
        if (level == null || level.isBlank()) {
            return false;
        }

        GeoProperties.Levels levels = geoProperties.levels();
        if (levels == null || levels.enabled() == null || levels.enabled().isEmpty()) {
            return true;
        }

        String normalized = level.trim().toLowerCase(Locale.ROOT);
        List<String> enabled = levels.enabled().stream()
                .map(v -> v == null ? null : v.trim().toLowerCase(Locale.ROOT))
                .toList();

        return enabled.contains(normalized);
    }

    public void assertEnabled(String level) {
        if (!isEnabled(level)) {
            throw new IllegalStateException("Geo level is disabled by configuration: " + level);
        }
    }
}