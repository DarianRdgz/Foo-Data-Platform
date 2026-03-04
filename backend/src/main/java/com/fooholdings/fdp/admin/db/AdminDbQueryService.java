package com.fooholdings.fdp.admin.db;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminDbQueryService {

    private final JdbcTemplate jdbc;

    public AdminDbQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * FDP-owned tables:
     * - any regular table (relkind='r') in schemas starting with 'fdp_'
     */
    public List<DbTableDto> listTables() {
        return jdbc.query(
                """
                SELECT
                  n.nspname AS schema_name,
                  c.relname AS table_name,
                  c.reltuples::bigint AS row_estimate,
                  pg_total_relation_size(c.oid) AS total_bytes,
                  pg_size_pretty(pg_total_relation_size(c.oid)) AS total_pretty,
                  s.last_vacuum,
                  s.last_autovacuum,
                  s.last_analyze,
                  s.last_autoanalyze
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                WHERE c.relkind = 'r'
                  AND n.nspname LIKE 'fdp\\_%' ESCAPE '\\'
                ORDER BY total_bytes DESC NULLS LAST, schema_name ASC, table_name ASC
                """,
                (rs, rowNum) -> new DbTableDto(
                        rs.getString("schema_name"),
                        rs.getString("table_name"),
                        rs.getLong("row_estimate"),
                        rs.getLong("total_bytes"),
                        rs.getString("total_pretty"),
                        tsToInstant(rs.getTimestamp("last_vacuum")),
                        tsToInstant(rs.getTimestamp("last_autovacuum")),
                        tsToInstant(rs.getTimestamp("last_analyze")),
                        tsToInstant(rs.getTimestamp("last_autoanalyze"))
                )
        );
    }

    public DbTableDto refreshOne(String schema, String table) {
        assertAllowed(schema, table);

        // Stats row may not exist yet (brand new table). Left join + query list avoids EmptyResult edge case.
        List<DbTableDto> out = jdbc.query(
                """
                SELECT
                  n.nspname AS schema_name,
                  c.relname AS table_name,
                  c.reltuples::bigint AS row_estimate,
                  pg_total_relation_size(c.oid) AS total_bytes,
                  pg_size_pretty(pg_total_relation_size(c.oid)) AS total_pretty,
                  s.last_vacuum,
                  s.last_autovacuum,
                  s.last_analyze,
                  s.last_autoanalyze
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                WHERE c.relkind = 'r'
                  AND n.nspname = ?
                  AND c.relname = ?
                """,
                (rs, rowNum) -> new DbTableDto(
                        rs.getString("schema_name"),
                        rs.getString("table_name"),
                        rs.getLong("row_estimate"),
                        rs.getLong("total_bytes"),
                        rs.getString("total_pretty"),
                        tsToInstant(rs.getTimestamp("last_vacuum")),
                        tsToInstant(rs.getTimestamp("last_autovacuum")),
                        tsToInstant(rs.getTimestamp("last_analyze")),
                        tsToInstant(rs.getTimestamp("last_autoanalyze"))
                ),
                schema, table
        );

        if (out.isEmpty()) {
            // Should not happen if assertAllowed passed, but keep it defensive.
            throw new EmptyResultDataAccessException("No stats row for " + schema + "." + table, 1);
        }
        return out.get(0);
    }

    public List<DbColumnDto> listColumns(String schema, String table) {
        assertAllowed(schema, table);

        return jdbc.query(
                """
                SELECT
                  column_name,
                  data_type,
                  is_nullable,
                  column_default
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                ORDER BY ordinal_position ASC
                """,
                (rs, rowNum) -> new DbColumnDto(
                        rs.getString("column_name"),
                        rs.getString("data_type"),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        rs.getString("column_default")
                ),
                schema, table
        );
    }

    public List<DbIndexDto> listIndexes(String schema, String table) {
        assertAllowed(schema, table);

        return jdbc.query(
                """
                SELECT
                  i.relname AS index_name,
                  ix.indisunique AS is_unique,
                  array_agg(a.attname ORDER BY u.ordinality) AS cols
                FROM pg_class t
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_index ix ON ix.indrelid = t.oid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS u(attnum, ordinality) ON true
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum
                WHERE n.nspname = ?
                  AND t.relname = ?
                GROUP BY i.relname, ix.indisunique
                ORDER BY i.relname
                """,
                (rs, rowNum) -> {
                    String indexName = rs.getString("index_name");
                    boolean unique = rs.getBoolean("is_unique");
                    Object arr = rs.getArray("cols").getArray();

                    List<String> cols = new ArrayList<>();
                    if (arr instanceof Object[] oa) {
                        for (Object o : oa) cols.add(String.valueOf(o));
                    }
                    return new DbIndexDto(indexName, unique, cols);
                },
                schema, table
        );
    }

    public DbSampleDto sampleRows(String schema, String table, int limit) {
        assertAllowed(schema, table);

        int safeLimit = Math.min(Math.max(limit, 1), 200);
        List<String> pkCols = primaryKeyColumns(schema, table);

        String orderBy = pkCols.isEmpty()
                ? "ctid DESC"
                : quoteIdent(pkCols.get(0)) + " DESC";

        String fqtn = quoteIdent(schema) + "." + quoteIdent(table);
        String sql = "SELECT * FROM " + fqtn + " ORDER BY " + orderBy + " LIMIT ?";

        List<Map<String, Object>> rows = jdbc.query(
                sql,
                (rs, rowNum) -> {
                    var md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        m.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    return m;
                },
                safeLimit
        );

        return new DbSampleDto(schema, table, pkCols, rows);
    }

    private List<String> primaryKeyColumns(String schema, String table) {
        return jdbc.query(
                """
                SELECT a.attname AS col
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN LATERAL unnest(ix.indkey) WITH ORDINALITY AS u(attnum, ordinality) ON true
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = u.attnum
                WHERE ix.indisprimary = true
                  AND n.nspname = ?
                  AND t.relname = ?
                ORDER BY u.ordinality
                """,
                (rs, rowNum) -> rs.getString("col"),
                schema, table
        );
    }

    private void assertAllowed(String schema, String table) {
        Boolean ok = jdbc.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                  WHERE c.relkind = 'r'
                    AND n.nspname LIKE 'fdp\\_%' ESCAPE '\\'
                    AND n.nspname = ?
                    AND c.relname = ?
                )
                """,
                Boolean.class,
                schema, table
        );

        if (ok == null || !ok) {
            throw new IllegalArgumentException("Table not allowed: " + schema + "." + table);
        }
    }

    private static Instant tsToInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}