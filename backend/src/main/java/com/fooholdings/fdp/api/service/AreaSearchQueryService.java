package com.fooholdings.fdp.api.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.AreaSearchResponse;
import com.fooholdings.fdp.api.dto.AreaSearchResult;

@Service
public class AreaSearchQueryService {

    private static final int DEFAULT_LIMIT = 10;

    private final JdbcTemplate jdbc;

    public AreaSearchQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AreaSearchResponse search(String query, String level) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("q is required");
        }

        String trimmedQuery = query.trim();
        if (trimmedQuery.length() < 2) {
            throw new IllegalArgumentException("q must be at least 2 characters");
        }

        String normalizedGeoLevel = GeoLevelValidator.requireSupported(level);
        String normalizedQuery = trimmedQuery.toLowerCase();
        String containsPattern = "%" + normalizedQuery + "%";
        String prefixPattern = normalizedQuery + "%";

        String sql = """
                select
                    g.geo_id,
                    g.geo_level::text as geo_level,
                    g.name,
                    g.display_label,
                    g.fips_code,
                    g.cbsa_code,
                    g.zip_code
                from fdp_geo.geo_areas g
                where g.geo_level = ?::fdp_geo.geo_level
                  and (
                    g.display_label ilike ?
                    or g.name ilike ?
                  )
                order by
                  case
                    when lower(g.display_label) = lower(?) then 0
                    when lower(g.name) = lower(?) then 1
                    when lower(g.display_label) like lower(?) then 2
                    when lower(g.name) like lower(?) then 3
                    else 4
                  end,
                  g.display_label
                limit ?
                """;

        List<AreaSearchResult> results = jdbc.query(
                sql,
                (rs, rowNum) -> new AreaSearchResult(
                        rs.getString("geo_id"),
                        rs.getString("geo_level"),
                        rs.getString("name"),
                        rs.getString("display_label"),
                        rs.getString("fips_code"),
                        rs.getString("cbsa_code"),
                        rs.getString("zip_code")
                ),
                normalizedGeoLevel,
                containsPattern,
                containsPattern,
                normalizedQuery,
                normalizedQuery,
                prefixPattern,
                prefixPattern,
                DEFAULT_LIMIT
        );

        return new AreaSearchResponse(trimmedQuery, normalizedGeoLevel, results);
    }
}
