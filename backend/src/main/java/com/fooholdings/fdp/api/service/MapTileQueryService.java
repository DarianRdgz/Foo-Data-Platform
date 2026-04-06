package com.fooholdings.fdp.api.service;

import java.math.BigDecimal;
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
        String normalizedGeoLevel = GeoLevelValidator.requireSupportedMapLevel(geoLevel);
        Bbox parsed = parseBbox(bbox);

        String sql = """
                with latest_risk as (
                    select
                        s.geo_id,
                        nullif(s.payload->>'riskTier', '') as risk_tier,
                        nullif(s.payload->>'tier', '') as fallback_tier,
                        cast(s.payload->>'riskScore' as double precision) as risk_score
                    from (
                        select
                            s.*,
                            row_number() over (
                                partition by s.geo_id
                                order by s.snapshot_period desc, s.ingested_at desc
                            ) as rn
                        from fdp_geo.area_snapshot s
                        where s.category = 'risk.composite'
                    ) s
                    where s.rn = 1
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
                    g.centroid_latitude,
                    g.centroid_longitude,
                    coalesce(lr.risk_tier, lr.fallback_tier) as risk_tier,
                    lr.risk_score,
                    coalesce(cc.category_count, 0) as category_count
                from fdp_geo.geo_areas g
                left join latest_risk lr on lr.geo_id = g.geo_id
                left join category_counts cc on cc.geo_id = g.geo_id
                where g.geo_level = ?::fdp_geo.geo_level
                  and (
                        ? = false
                     or (
                            g.centroid_longitude is not null
                        and g.centroid_latitude is not null
                        and g.centroid_longitude between ? and ?
                        and g.centroid_latitude between ? and ?
                     )
                  )
                order by g.display_label
                """;

        boolean useCentroidFilter = isCentroidFilteredLevel(normalizedGeoLevel);

        List<GeoJsonFeatureCollection.Feature> features = new ArrayList<>();
        jdbc.query(
                sql,
                rs -> {
                    Map<String, Object> properties = new LinkedHashMap<>();
                    properties.put("geoId", rs.getString("geo_id"));
                    properties.put("geoLevel", rs.getString("geo_level"));
                    properties.put("name", rs.getString("name"));
                    properties.put("displayLabel", rs.getString("display_label"));

                    putIfPresent(properties, "fipsCode", rs.getString("fips_code"));
                    putIfPresent(properties, "cbsaCode", rs.getString("cbsa_code"));
                    putIfPresent(properties, "zipCode", rs.getString("zip_code"));
                    putIfPresent(properties, "riskTier", rs.getString("risk_tier"));

                    Object riskScore = rs.getObject("risk_score");
                    if (riskScore != null) {
                        properties.put("riskScore", riskScore);
                    }

                    Object categoryCount = rs.getObject("category_count");
                    if (categoryCount != null) {
                        properties.put("categoryCount", categoryCount);
                    }

                    BigDecimal centroidLatitude = rs.getBigDecimal("centroid_latitude");
                    BigDecimal centroidLongitude = rs.getBigDecimal("centroid_longitude");
                    if (centroidLatitude != null && centroidLongitude != null) {
                        properties.put("centroidLatitude", centroidLatitude);
                        properties.put("centroidLongitude", centroidLongitude);
                    }

                    properties.put("boundaryKind", normalizedGeoLevel);
                    properties.put("boundaryKey", resolveBoundaryKey(
                            normalizedGeoLevel,
                            rs.getString("fips_code"),
                            rs.getString("cbsa_code"),
                            rs.getString("zip_code")
                    ));

                    features.add(new GeoJsonFeatureCollection.Feature(
                            "Feature",
                            null,
                            properties
                    ));
                },
                normalizedGeoLevel,
                useCentroidFilter,
                parsed.west(),
                parsed.east(),
                parsed.south(),
                parsed.north()
        );

        return new GeoJsonFeatureCollection("FeatureCollection", features);
    }

    private boolean isCentroidFilteredLevel(String geoLevel) {
        return switch (geoLevel) {
            // State tiles intentionally ignore bbox so the initial national paint
            // always returns the full state set and the frontend has a stable first render.
            case "state" -> false;
            case "county", "metro", "city", "zip" -> true;
            default -> false;
        };
    }

    private String resolveBoundaryKey(String geoLevel, String fipsCode, String cbsaCode, String zipCode) {
        return switch (geoLevel) {
            case "state", "county" -> fipsCode;
            case "metro" -> cbsaCode;
            case "zip" -> zipCode;
            default -> null;
        };
    }

    private void putIfPresent(Map<String, Object> properties, String key, String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private Bbox parseBbox(String bbox) {
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

        return new Bbox(west, south, east, north);
    }

    private double parseCoordinate(String rawValue, String label) {
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("bbox contains an invalid " + label + " coordinate", ex);
        }
    }

    private record Bbox(double west, double south, double east, double north) {}
}
