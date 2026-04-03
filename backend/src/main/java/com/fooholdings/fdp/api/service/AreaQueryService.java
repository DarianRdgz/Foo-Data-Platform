package com.fooholdings.fdp.api.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fooholdings.fdp.api.dto.AreaResponse;
import com.fooholdings.fdp.api.dto.AreaSnapshotView;
import com.fooholdings.fdp.api.dto.ChangeBadgeView;
import com.fooholdings.fdp.api.dto.GeoParentView;
import com.fooholdings.fdp.geo.support.GeoAreaNotFoundException;

@Service
public class AreaQueryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    public AreaQueryService(JdbcTemplate jdbc, JsonMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public AreaResponse getArea(String geoLevel, String geoIdOrNaturalKey) {
        String normalizedGeoLevel = GeoLevelValidator.requireSupported(geoLevel);
        GeoMetadata meta = loadGeoMetadata(normalizedGeoLevel, geoIdOrNaturalKey);
        if (meta == null) {
            throw new GeoAreaNotFoundException(normalizedGeoLevel, geoIdOrNaturalKey);
        }

        List<AreaSnapshotView> snapshots = loadLatestSnapshots(meta.geoId());

        return new AreaResponse(
                meta.geoId(),
                meta.geoLevel(),
                meta.name(),
                meta.displayLabel(),
                meta.fipsCode(),
                meta.cbsaCode(),
                meta.zipCode(),
                meta.parent(),
                null,
                snapshots
        );
    }

    private GeoMetadata loadGeoMetadata(String geoLevel, String idOrNaturalKey) {
        List<GeoMetadata> rows = jdbc.query(
                """
                select
                    g.geo_id,
                    g.geo_level::text as geo_level,
                    g.name,
                    g.display_label,
                    g.fips_code,
                    g.cbsa_code,
                    g.zip_code,
                    p.geo_id as parent_geo_id,
                    p.geo_level::text as parent_geo_level,
                    p.name as parent_name,
                    p.display_label as parent_display_label
                from fdp_geo.geo_areas g
                left join fdp_geo.geo_areas p
                  on p.geo_id = g.parent_geo_id
                where g.geo_level = ?::fdp_geo.geo_level
                  and (
                        cast(g.geo_id as text) = ?
                     or coalesce(g.fips_code, '') = ?
                     or coalesce(g.cbsa_code, '') = ?
                     or coalesce(g.zip_code, '') = ?
                  )
                limit 1
                """,
                (rs, rowNum) -> mapGeoMetadata(rs),
                geoLevel, idOrNaturalKey, idOrNaturalKey, idOrNaturalKey, idOrNaturalKey
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private GeoMetadata mapGeoMetadata(ResultSet rs) throws SQLException {
        GeoParentView parent = null;
        String parentId = rs.getString("parent_geo_id");
        if (parentId != null) {
            parent = new GeoParentView(
                    UUID.fromString(parentId),
                    rs.getString("parent_geo_level"),
                    rs.getString("parent_name"),
                    rs.getString("parent_display_label")
            );
        }
        return new GeoMetadata(
                UUID.fromString(rs.getString("geo_id")),
                rs.getString("geo_level"),
                rs.getString("name"),
                rs.getString("display_label"),
                rs.getString("fips_code"),
                rs.getString("cbsa_code"),
                rs.getString("zip_code"),
                parent
        );
    }

    private List<AreaSnapshotView> loadLatestSnapshots(UUID geoId) {
        return jdbc.query(
                """
                with ranked as (
                    select
                        s.geo_id,
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
                select
                    r.category,
                    r.snapshot_period,
                    r.source,
                    r.is_rollup,
                    r.payload_text,
                    cl.prior_period,
                    cl.current_period,
                    cl.pct_change,
                    cl.direction::text as direction,
                    cl.magnitude
                from ranked r
                left join fdp_geo.area_change_log cl
                  on cl.geo_id = ?
                 and cl.category = r.category
                 and cl.current_period = r.snapshot_period
                where r.rn = 1
                order by r.category
                """,
                (rs, rowNum) -> {
                    ChangeBadgeView badge = null;
                    Number pct = (Number) rs.getObject("pct_change");
                    if (pct != null) {
                        badge = new ChangeBadgeView(
                                rs.getString("category"),
                                rs.getDate("prior_period").toLocalDate(),
                                rs.getDate("current_period").toLocalDate(),
                                pct.doubleValue(),
                                rs.getString("direction"),
                                rs.getString("magnitude")
                        );
                    }
                    return new AreaSnapshotView(
                            rs.getString("category"),
                            rs.getDate("snapshot_period").toLocalDate(),
                            rs.getString("source"),
                            rs.getBoolean("is_rollup"),
                            parsePayload(rs.getString("payload_text")),
                            badge
                    );
                },
                geoId, geoId
        );
    }

    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException | IllegalArgumentException e) {
            return Map.of("_parseError", "payload could not be deserialized");
        }
    }

    private record GeoMetadata(
            UUID geoId,
            String geoLevel,
            String name,
            String displayLabel,
            String fipsCode,
            String cbsaCode,
            String zipCode,
            GeoParentView parent
    ) {}
}
