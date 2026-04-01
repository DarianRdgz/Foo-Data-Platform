package com.fooholdings.fdp.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.AreaResponse;
import com.fooholdings.fdp.api.dto.AreaSnapshotView;
import com.fooholdings.fdp.geo.support.GeoAreaNotFoundException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves the latest area snapshot data for a single geographic unit.
 *
 * Intentionally narrow for 5.4:
 *   - Returns geo metadata + latest snapshot per category
 *   - Does NOT return history, children, or map tiles (deferred to 5.5)
 *
 * The "latest per category" query uses ROW_NUMBER() partitioned by
 * (geo_id, category) ordered by snapshot_period desc, ingested_at desc.
 * This ensures one row per category regardless of how many periods exist.
 */
@Service
public class AreaQueryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AreaQueryService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public AreaResponse getArea(String geoLevel, UUID geoId) {
        // Validate the geo exists and the requested level matches what is stored
        GeoMetadata meta = loadGeoMetadata(geoLevel, geoId);
        if (meta == null) {
            throw new GeoAreaNotFoundException(geoLevel, geoId.toString());
        }

        List<AreaSnapshotView> snapshots = loadLatestSnapshots(geoId);

        return new AreaResponse(
                geoId,
                meta.geoLevel(),
                meta.name(),
                meta.displayLabel(),
                meta.fipsCode(),
                meta.cbsaCode(),
                meta.zipCode(),
                snapshots
        );
    }

    // ── Geo metadata ──────────────────────────────────────────────────────

    private GeoMetadata loadGeoMetadata(String geoLevel, UUID geoId) {
        ResultSetExtractor<GeoMetadata> extractor = rs -> rs.next() ? mapGeoMetadata(rs) : null;
        return jdbc.query(
                """
                select geo_level, name, display_label, fips_code, cbsa_code, zip_code
                from fdp_geo.geo_areas
                where geo_id = ?
                  and geo_level = ?::fdp_geo.geo_level
                """,
                extractor,
                geoId, geoLevel
        );
    }

    private static GeoMetadata mapGeoMetadata(ResultSet rs) throws SQLException {
        return new GeoMetadata(
                rs.getString("geo_level"),
                rs.getString("name"),
                rs.getString("display_label"),
                rs.getString("fips_code"),
                rs.getString("cbsa_code"),
                rs.getString("zip_code")
        );
    }

    // ── Snapshot query ────────────────────────────────────────────────────

    private List<AreaSnapshotView> loadLatestSnapshots(UUID geoId) {
        List<AreaSnapshotView> result = new ArrayList<>();
        RowCallbackHandler rowCallback = rs -> {
            while (rs.next()) {
                Map<String, Object> payload = parsePayload(rs.getString("payload_text"));
                LocalDate period = rs.getDate("snapshot_period").toLocalDate();

                result.add(new AreaSnapshotView(
                        rs.getString("category"),
                        period,
                        rs.getString("source"),
                        rs.getBoolean("is_rollup"),
                        payload
                ));
            }
        };

        jdbc.query(
                """
                with ranked as (
                    select
                        s.category,
                        s.snapshot_period,
                        s.source,
                        s.is_rollup,
                        s.payload::text as payload_text,
                        row_number() over (
                            partition by s.geo_id, s.category
                            order by s.snapshot_period desc, s.ingested_at desc
                        ) as rn
                    from fdp_geo.area_snapshot s
                    where s.geo_id = ?
                )
                select category, snapshot_period, source, is_rollup, payload_text
                from ranked
                where rn = 1
                order by category
                """,
                rowCallback,
                geoId
        );

        return result;
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException ignored) {
            return Map.of("_parseError", "payload could not be deserialized");
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────

    private record GeoMetadata(
            String geoLevel,
            String name,
            String displayLabel,
            String fipsCode,
            String cbsaCode,
            String zipCode
    ) {}
}
