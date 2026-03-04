package com.fooholdings.fdp.admin.ingestion;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminIngestionQueryService {

    private final JdbcTemplate jdbc;
    private final FdpQuotaProperties quotaProps;

    public AdminIngestionQueryService(JdbcTemplate jdbc, FdpQuotaProperties quotaProps) {
        this.jdbc = jdbc;
        this.quotaProps = quotaProps;
    }

    public PageDto<IngestionRunRowDto> listRuns(String source, String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        String src = nullIfBlank(source);
        String st = nullIfBlank(status);

        // Build WHERE only for filters that exist (index-friendly).
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();

        if (src != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("ss.code = ?");
            args.add(src);
        }

        if (st != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            // status is a postgres enum -> cast param to enum type
            where.append("r.status = ?::ingestion_status");
            args.add(st);
        }

        String fromJoin =
                " FROM fdp_core.ingestion_run r " +
                " JOIN fdp_core.source_system ss ON ss.id = r.source_system_id ";

        // COUNT query
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*)" + fromJoin + where,
                Long.class,
                args.toArray()
        );
        long totalSafe = total == null ? 0L : total;

        // DATA query
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(safeSize);
        dataArgs.add(offset);

        List<IngestionRunRowDto> rows = jdbc.query(
                "SELECT r.id, ss.code AS source, r.status, r.started_at, r.finished_at, " +
                "       r.records_written, r.message " +
                fromJoin +
                where +
                " ORDER BY r.started_at DESC " +
                " LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapRunRow(rs),
                dataArgs.toArray()
        );

        return new PageDto<>(rows, safePage, safeSize, totalSafe);
    }

    public IngestionRunDetailDto getRun(UUID runId) {
        return jdbc.queryForObject(
                "SELECT r.id, ss.code AS source, r.status, r.started_at, r.finished_at, " +
                "       r.records_written, r.message, r.requested_scope_json::text, " +
                "       r.locked_by, r.locked_at, r.error_detail " +
                "FROM fdp_core.ingestion_run r " +
                "JOIN fdp_core.source_system ss ON ss.id = r.source_system_id " +
                "WHERE r.id = ?",
                (rs, rowNum) -> mapRunDetail(rs),
                runId
        );
    }

    public List<QuotaSummaryDto> quotaToday() {
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> used = jdbc.queryForList(
                "SELECT ss.code AS source, COALESCE(SUM(q.call_count), 0) AS used_today " +
                "FROM fdp_core.source_system ss " +
                "LEFT JOIN fdp_core.api_quota_usage q " +
                "  ON q.source_system_id = ss.id AND q.usage_date = ? " +
                "GROUP BY ss.code " +
                "ORDER BY ss.code",
                today
        );

        Map<String, Integer> limits = quotaProps.dailyLimits() == null ? Map.of() : quotaProps.dailyLimits();

        return used.stream().map(row -> {
            String src = (String) row.get("source");
            int usedToday = ((Number) row.get("used_today")).intValue();
            int limit = limits.getOrDefault(src, 0);
            int remaining = Math.max(limit - usedToday, 0);
            return new QuotaSummaryDto(src, today, usedToday, limit, remaining);
        }).toList();
    }

    private static IngestionRunRowDto mapRunRow(ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String src = rs.getString("source");
        String status = rs.getString("status");
        Instant started = rs.getTimestamp("started_at").toInstant();
        Instant finished = rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null;

        long durationMs = computeDurationMs(started, finished);
        int recordsWritten = rs.getInt("records_written");
        String msg = rs.getString("message");

        return new IngestionRunRowDto(
                id, src, status, started, finished, durationMs, recordsWritten,
                "FAILED".equalsIgnoreCase(status) ? msg : null
        );
    }

    private static IngestionRunDetailDto mapRunDetail(ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String src = rs.getString("source");
        String status = rs.getString("status");
        Instant started = rs.getTimestamp("started_at").toInstant();
        Instant finished = rs.getTimestamp("finished_at") != null ? rs.getTimestamp("finished_at").toInstant() : null;

        return new IngestionRunDetailDto(
                id,
                src,
                status,
                started,
                finished,
                computeDurationMs(started, finished),
                rs.getInt("records_written"),
                rs.getString("message"),
                rs.getString("requested_scope_json"),
                rs.getString("locked_by"),
                rs.getTimestamp("locked_at") != null ? rs.getTimestamp("locked_at").toInstant() : null,
                rs.getString("error_detail")
        );
    }

    private static long computeDurationMs(Instant started, Instant finished) {
        Instant end = finished != null ? finished : Instant.now();
        return Math.max(0L, end.toEpochMilli() - started.toEpochMilli());
    }

    private static String nullIfBlank(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}