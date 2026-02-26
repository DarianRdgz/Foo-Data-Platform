package com.fooholdings.fdp.core.source;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SourceSystemService {

    private final JdbcTemplate jdbc;
    private final ConcurrentHashMap<String, Short> cache = new ConcurrentHashMap<>();

    public SourceSystemService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public short getRequiredIdByCode(String code) {
        return cache.computeIfAbsent(code, c -> {
            Short id = jdbc.query(
                    "select id from fdp_core.source_system where code = ?",
                    rs -> rs.next() ? rs.getShort(1) : null,
                    c
            );

            if (id == null) {
                throw new IllegalStateException(
                        "Missing fdp_core.source_system row for code=" + c +
                        ". Seed it in Flyway (e.g., insert KROGER)."
                );
            }
            return id;
        });
    }
}