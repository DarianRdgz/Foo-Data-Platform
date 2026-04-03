package com.fooholdings.fdp.geo.repo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.json.JsonMapper;

@Repository
public class AreaSnapshotJdbcRepository {

    private static final String UPSERT_SQL = """
            insert into fdp_geo.area_snapshot
                (geo_id, category, snapshot_period, source, is_rollup, payload)
            values
                (:geoId, :category, :snapshotPeriod, :source, false, CAST(:payload AS jsonb))
            on conflict (geo_id, category, snapshot_period, source)
            do update set
                payload = excluded.payload,
                ingested_at = now(),
                is_rollup = false
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper objectMapper;

    public AreaSnapshotJdbcRepository(NamedParameterJdbcTemplate jdbc, JsonMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public int batchUpsert(List<AreaSnapshotUpsert> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        List<MapSqlParameterSource> batch = new ArrayList<>(rows.size());
        for (AreaSnapshotUpsert row : rows) {
            batch.add(new MapSqlParameterSource()
                    .addValue("geoId", row.geoId())
                    .addValue("category", row.category())
                    .addValue("snapshotPeriod", row.snapshotPeriod())
                    .addValue("source", row.source())
                    .addValue("payload", toJson(row.payload())));
        }

        int[] results = jdbc.batchUpdate(UPSERT_SQL, batch.toArray(MapSqlParameterSource[]::new));

        int written = 0;
        for (int result : results) {
            if (result >= 0) {
                written += result;
            } else {
                written += 1;
            }
        }
        return written;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize area_snapshot payload", e);
        }
    }

    public record AreaSnapshotUpsert(
            UUID geoId,
            String category,
            LocalDate snapshotPeriod,
            String source,
            Map<String, Object> payload
    ) {}
}
