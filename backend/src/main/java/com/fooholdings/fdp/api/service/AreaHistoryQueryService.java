package com.fooholdings.fdp.api.service;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.HistoryPoint;
import com.fooholdings.fdp.api.dto.HistoryResponse;
import com.fooholdings.fdp.geo.support.GeoAreaNotFoundException;

@Service
public class AreaHistoryQueryService {

    private final JdbcTemplate jdbc;

    public AreaHistoryQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HistoryResponse getHistory(String geoLevel, String geoIdOrNaturalKey, String category, int periods) {
        String normalizedGeoLevel = GeoLevelValidator.requireSupported(geoLevel);
        requireCategory(category);
        requirePositivePeriods(periods);

        UUID geoId = resolveGeoId(normalizedGeoLevel, geoIdOrNaturalKey);

        List<HistoryPoint> points = jdbc.query(
                """
                select snapshot_period, (payload->>'value')::double precision as value, source, is_rollup
                from (
                    select
                        s.*,
                        row_number() over (
                            partition by s.snapshot_period
                            order by s.is_rollup asc, s.ingested_at desc
                        ) as rn
                    from fdp_geo.area_snapshot s
                    where s.geo_id = ?
                      and s.category = ?
                ) ranked
                where rn = 1
                order by snapshot_period desc
                limit ?
                """,
                (rs, rowNum) -> new HistoryPoint(
                        rs.getDate("snapshot_period").toLocalDate(),
                        (Double) rs.getObject("value"),
                        rs.getString("source"),
                        rs.getBoolean("is_rollup")
                ),
                geoId, category, periods
        );

        java.util.Collections.reverse(points);
        return new HistoryResponse(geoId, normalizedGeoLevel, category, periods, points);
    }

    private void requireCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
    }

    private void requirePositivePeriods(int periods) {
        if (periods < 1) {
            throw new IllegalArgumentException("periods must be greater than 0");
        }
    }

    private UUID resolveGeoId(String geoLevel, String idOrNaturalKey) {
        List<UUID> ids = jdbc.query(
                """
                select g.geo_id
                from fdp_geo.geo_areas g
                where g.geo_level = ?::fdp_geo.geo_level
                  and (
                        cast(g.geo_id as text) = ?
                     or coalesce(g.fips_code, '') = ?
                     or coalesce(g.cbsa_code, '') = ?
                     or coalesce(g.zip_code, '') = ?
                  )
                limit 1
                """,
                (rs, rowNum) -> UUID.fromString(rs.getString("geo_id")),
                geoLevel, idOrNaturalKey, idOrNaturalKey, idOrNaturalKey, idOrNaturalKey
        );
        if (ids.isEmpty()) {
            throw new GeoAreaNotFoundException(geoLevel, idOrNaturalKey);
        }
        return ids.get(0);
    }
}
