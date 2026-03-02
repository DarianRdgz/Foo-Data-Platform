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

    // code -> id
    private final ConcurrentHashMap<String, Short> codeToId = new ConcurrentHashMap<>();
    // id -> code
    private final ConcurrentHashMap<Short, String> idToCode = new ConcurrentHashMap<>();

    public SourceSystemService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the SMALLINT id for the given source code (cached). */
    public short getRequiredIdByCode(String code) {
        short id = codeToId.computeIfAbsent(code, this::fetchIdFromDb);
        idToCode.putIfAbsent(id, code);
        return id;
    }

    /**
     * Returns the code string for the given SMALLINT id (cached).
     *
     * @throws IllegalStateException if no row exists with the given id
     */
    public String getRequiredCodeById(short id) {
        return idToCode.computeIfAbsent(id, k -> fetchCodeFromDb(k));
    }

    private short fetchIdFromDb(String code) {
        Short id = jdbc.query(
                "SELECT id FROM fdp_core.source_system WHERE code = ?",
                rs -> rs.next() ? rs.getShort(1) : null,
                code
        );
        if (id == null) {
            throw new IllegalStateException(
                    "No row in fdp_core.source_system for code='" + code + "'. " +
                    "Ensure your seed migration has been applied."
            );
        }
        return id;
    }

    private String fetchCodeFromDb(short id) {
        String code = jdbc.query(
                "SELECT code FROM fdp_core.source_system WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                id
        );
        if (code == null) {
            throw new IllegalStateException(
                    "No row in fdp_core.source_system for id=" + id + ". " +
                    "Ensure your seed migration has been applied."
            );
        }
        // Keep both caches warm.
        codeToId.putIfAbsent(code, id);
        return code;
    }
}
