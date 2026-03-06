package com.fooholdings.fdp.geo.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geo-layer feature flags and seeding configuration for Sprint 5.
 *
 * <pre>
 * fdp:
 *   geo:
 *     levels:
 *       enabled: [national, state, metro, county, city, zip]
 *     seeding:
 *       enabled: false
 *       seedDir: /opt/fdp/seed
 * </pre>
 */
@ConfigurationProperties(prefix = "fdp.geo")
public record GeoProperties(Levels levels, Seeding seeding) {

    public record Levels(List<String> enabled) {
    }

    public record Seeding(boolean enabled, String seedDir) {
    }
}