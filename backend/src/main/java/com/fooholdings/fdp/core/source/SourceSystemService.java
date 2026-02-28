package com.fooholdings.fdp.core.source;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves fdp_core.source_system rows to their SMALLINT primary key.
 *
 * Results are cached in memory for the lifetime of the application.
 * If a new source is added to the source_system table after startup,
 * the application must be restarted to pick it up. This is intentional
 * source systems are infrastructure-level config, not runtime data.
 *
 * Usage: sourceSystemService.getRequiredIdByCode("-enter ID here-")
 *
 * Throws IllegalStateException immediately to surface misconfigured environments
 * to not silently write null foreign keys.
 * V8 Flyway migration seeds the initial rows.
 */
@Service
public class SourceSystemService {

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Short> cache = new ConcurrentHashMap<>();

    public SourceSystemService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the SMALLINT id for the given source code.
     * Caches on first call. Thread-safe via ConcurrentHashMap.computeIfAbsent.
     *
     * @throws IllegalStateException if no row exists with the given code
     */
    public short getRequiredIdByCode(String code) {
        return cache.computeIfAbsent(code, this::fetchFromDb);
    }

    private short fetchFromDb(String code) {
        Short id = jdbc.query(
                "SELECT id FROM fdp_core.source_system WHERE code = ?",
                rs -> rs.next() ? rs.getShort(1) : null,
                code
        );
        if (id == null) {
            throw new IllegalStateException(
                    "No row in fdp_core.source_system for code='" + code + "'. " +
                    "Ensure V8__seed_source_system.sql has been applied."
            );
        }
        return id;
    }
}
