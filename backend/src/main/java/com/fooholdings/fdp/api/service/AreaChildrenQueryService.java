package com.fooholdings.fdp.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.ChildAreaView;
import com.fooholdings.fdp.api.dto.ChildrenResponse;
import com.fooholdings.fdp.geo.support.GeoAreaNotFoundException;

@Service
public class AreaChildrenQueryService {

    private final JdbcTemplate jdbc;

    public AreaChildrenQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ChildrenResponse getChildren(String geoLevel, String geoIdOrNaturalKey) {
        String normalizedGeoLevel = GeoLevelValidator.requireSupported(geoLevel);
        UUID geoId = resolveGeoId(normalizedGeoLevel, geoIdOrNaturalKey);
        String preferredChildLevel = resolvePreferredChildLevel(normalizedGeoLevel);

        StringBuilder sql = new StringBuilder(
                """
                select
                    c.geo_id,
                    c.geo_level::text as geo_level,
                    c.name,
                    c.display_label,
                    c.fips_code,
                    c.cbsa_code,
                    c.zip_code,
                    count(distinct s.category) as coverage_count
                from fdp_geo.geo_areas c
                left join fdp_geo.area_snapshot s
                on s.geo_id = c.geo_id
                where c.parent_geo_id = ?
                """
        );

        List<Object> args = new ArrayList<>();
        args.add(geoId);

        if (preferredChildLevel != null) {
            sql.append("\n  and c.geo_level = ?::fdp_geo.geo_level");
            args.add(preferredChildLevel);
        }

        sql.append(
                """

                group by
                    c.geo_id,
                    c.geo_level,
                    c.name,
                    c.display_label,
                    c.fips_code,
                    c.cbsa_code,
                    c.zip_code
                order by c.display_label
                """
        );

        List<ChildAreaView> children = jdbc.query(
                sql.toString(),
                (rs, rowNum) -> new ChildAreaView(
                        UUID.fromString(rs.getString("geo_id")),
                        rs.getString("geo_level"),
                        rs.getString("name"),
                        rs.getString("display_label"),
                        rs.getLong("coverage_count"),
                        rs.getString("fips_code"),
                        rs.getString("cbsa_code"),
                        rs.getString("zip_code")
                ),
                args.toArray()
        );

        String childLevel = preferredChildLevel != null
                ? preferredChildLevel
                : (children.isEmpty() ? null : children.get(0).geoLevel());

        return new ChildrenResponse(geoId, normalizedGeoLevel, childLevel, children);
    }

    private String resolvePreferredChildLevel(String parentGeoLevel) {
        if ("state".equals(parentGeoLevel)) {
            return "county";
        }

        return null;
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
