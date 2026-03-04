package com.fooholdings.fdp.core.ingestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.api.dto.HealthResponse;

@Service
public class IngestionHealthService {

    private final JdbcTemplate jdbc;

    public IngestionHealthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public HealthResponse getHealth() {
        String sql = """
            SELECT
              ss.code AS source,
              ir.id AS run_id,
              ir.status,
              ir.started_at,
              ir.finished_at,
              ir.message
            FROM fdp_core.source_system ss
            LEFT JOIN LATERAL (
              SELECT *
              FROM fdp_core.ingestion_run r
              WHERE r.source_system_id = ss.id
              ORDER BY r.started_at DESC
              LIMIT 1
            ) ir ON true
            ORDER BY ss.code ASC
            """;

        List<HealthResponse.SourceIngestionStatus> rows = jdbc.query(sql, (rs, i) -> {
            UUID runId = (UUID) rs.getObject("run_id");
            Timestamp started = rs.getTimestamp("started_at");
            Timestamp finished = rs.getTimestamp("finished_at");

            return new HealthResponse.SourceIngestionStatus(
                    rs.getString("source"),
                    runId,
                    rs.getString("status"),
                    started != null ? started.toInstant() : null,
                    finished != null ? finished.toInstant() : null,
                    rs.getString("message")
            );
        });

        return new HealthResponse(Instant.now(), rows);
    }
}