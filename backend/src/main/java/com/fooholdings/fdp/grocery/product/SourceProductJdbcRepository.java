package com.fooholdings.fdp.grocery.product;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC write path for fdp_grocery.source_product.
 *
 * Upsert contract:
 *   ON CONFLICT (source_system_id, source_product_id) — deduplicates by natural key.
 *   product_id is deliberately excluded from the UPDATE clause; it is written by
 *   the canonicalization job and must not be overwritten by raw ingestion.
 *
 * JSONB fields (raw_category_json, raw_flags_json) are wrapped in PGobject.
 * Passing null is safe
 */
@Repository
public class SourceProductJdbcRepository {

    private final JdbcTemplate jdbc;

    public SourceProductJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID upsert(UUID id,
                       short sourceSystemId,
                       String sourceProductId,
                       String upc,
                       String name,
                       String brand,
                       String[] categories,
                       String productPageUri,
                       String rawCategoryJson,
                       String rawFlagsJson) {

        Instant now = Instant.now();

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                INSERT INTO fdp_grocery.source_product
                (id, source_system_id, source_product_id, upc, name, brand, categories,
                product_page_uri, raw_category_json, raw_flags_json,
                first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_system_id, source_product_id) DO UPDATE SET
                upc               = EXCLUDED.upc,
                name              = EXCLUDED.name,
                brand             = EXCLUDED.brand,
                categories        = EXCLUDED.categories,
                product_page_uri  = EXCLUDED.product_page_uri,
                raw_category_json = EXCLUDED.raw_category_json,
                raw_flags_json    = EXCLUDED.raw_flags_json,
                last_seen_at      = EXCLUDED.last_seen_at
                """);

            ps.setObject(1, id);
            ps.setShort(2, sourceSystemId);
            ps.setString(3, sourceProductId);
            ps.setString(4, upc);
            ps.setString(5, name);
            ps.setString(6, brand);

            if (categories == null) {
                ps.setArray(7, null);
            } else {
                Array arr = con.createArrayOf("text", categories);
                ps.setArray(7, arr);
            }

            ps.setString(8, productPageUri);
            ps.setObject(9, toJsonb(rawCategoryJson));
            ps.setObject(10, toJsonb(rawFlagsJson));
            ps.setObject(11, now);
            ps.setObject(12, now);
            return ps;
        });

        return jdbc.queryForObject(
                "SELECT id FROM fdp_grocery.source_product WHERE source_system_id = ? AND source_product_id = ?",
                UUID.class,
                sourceSystemId, sourceProductId
        );
    }

    private static PGobject toJsonb(String json) {
        if (json == null) return null;
        try {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(json);
            return obj;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to construct jsonb PGobject", e);
        }
    }
}
