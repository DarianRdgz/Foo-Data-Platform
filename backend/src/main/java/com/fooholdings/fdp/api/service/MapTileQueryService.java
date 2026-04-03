package com.fooholdings.fdp.api.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.GeoJsonFeatureCollection;

@Service
public class MapTileQueryService {

    private final JdbcTemplate jdbc;

    public MapTileQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GeoJsonFeatureCollection getTiles(String geoLevel, String bbox) {
        String normalizedGeoLevel = GeoLevelValidator.requireSupported(geoLevel);
        parseBbox(bbox);

        List<GeoJsonFeatureCollection.Feature> features = new ArrayList<>();
        jdbc.query(
                """
                with latest_risk as (
                    select distinct on (s.geo_id)
                        s.geo_id,
                        (s.payload->>'riskTier') as risk_tier,
                        (s.payload->>'tier') as fallback_tier,
                        (s.payload->>'riskScore')::double precision as risk_score
                    from fdp_geo.area_snapshot s
                    where s.category = 'risk.composite'
                    order by s.geo_id, s.snapshot_period desc, s.ingested_at desc
                ),
                category_counts as (
                    select geo_id, count(distinct category) as category_count
                    from fdp_geo.area_snapshot
                    group by geo_id
                )
                select
                    g.geo_id,
                    g.geo_level::text as geo_level,
                    g.name,
                    g.display_label,
                    g.fips_code,
                    g.cbsa_code,
                    g.zip_code,
                    coalesce(lr.risk_tier, lr.fallback_tier) as risk_tier,
                    lr.risk_score,
                    coalesce(cc.category_count, 0) as category_count
                from fdp_geo.geo_areas g
                left join latest_risk lr on lr.geo_id = g.geo_id
                left join category_counts cc on cc.geo_id = g.geo_id
                where g.geo_level = ?::fdp_geo.geo_level
                order by g.display_label
                """,
                rs -> {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    properties.put("geoId", rs.getString("geo_id"));
                    properties.put("geoLevel", rs.getString("geo_level"));
                    properties.put("name", rs.getString("name"));
                    properties.put("displayLabel", rs.getString("display_label"));

                    String fipsCode = rs.getString("fips_code");
                    if (fipsCode != null && !fipsCode.isBlank()) {
                        properties.put("fipsCode", fipsCode);
                    }

                    String cbsaCode = rs.getString("cbsa_code");
                    if (cbsaCode != null && !cbsaCode.isBlank()) {
                        properties.put("cbsaCode", cbsaCode);
                    }

                    String zipCode = rs.getString("zip_code");
                    if (zipCode != null && !zipCode.isBlank()) {
                        properties.put("zipCode", zipCode);
                    }

                    String riskTier = rs.getString("risk_tier");
                    if (riskTier != null && !riskTier.isBlank()) {
                        properties.put("riskTier", riskTier);
                    }

                    Object riskScore = rs.getObject("risk_score");
                    if (riskScore != null) {
                        properties.put("riskScore", riskScore);
                    }

                    Object categoryCount = rs.getObject("category_count");
                    if (categoryCount != null) {
                        properties.put("categoryCount", categoryCount);
                    }

                    features.add(new GeoJsonFeatureCollection.Feature(
                            "Feature",
                            null,
                            properties
                    ));
                },
                normalizedGeoLevel
        );

        return new GeoJsonFeatureCollection("FeatureCollection", features);
    }

    private void parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            throw new IllegalArgumentException("bbox is required and must be west,south,east,north");
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox must have exactly four comma-separated numbers");
        }

        double west = parseCoordinate(parts[0], "west");
        double south = parseCoordinate(parts[1], "south");
        double east = parseCoordinate(parts[2], "east");
        double north = parseCoordinate(parts[3], "north");

        if (west < -180 || east > 180) {
            throw new IllegalArgumentException("bbox longitude values must be between -180 and 180");
        }
        if (south < -90 || north > 90) {
            throw new IllegalArgumentException("bbox latitude values must be between -90 and 90");
        }
        if (west >= east) {
            throw new IllegalArgumentException("bbox west must be less than east");
        }
        if (south >= north) {
            throw new IllegalArgumentException("bbox south must be less than north");
        }
    }

    private double parseCoordinate(String rawValue, String label) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("bbox contains an invalid " + label + " coordinate", ex);
        }
    }
}
