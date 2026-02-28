package com.fooholdings.fdp.core.ingestion;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.source.SourceSystemService;

/**
 * Distributed ingestion lock backed by fdp_core.ingestion_lock (V3 migration).
 *
 * Design rationale:
 *   Advisory locks are tied to the database session. With HikariCP's
 *   connection pool to prevent acquiring and releasing a session-level advisory 
 *   lock on different borrowed connections. I used standard SQL visible to all 
 *   instances, which does not depend on connection identity.
 * 
 * Release safety:
 *   release() uses AND locked_by = ? so that an instance never accidentally
 *   releases a lock it does not own.
 */
@Service
public class IngestionLockService {

    private static final Logger log = LoggerFactory.getLogger(IngestionLockService.class);

    private final JdbcTemplate jdbc;
    private final SourceSystemService sourceSystemService;

    public IngestionLockService(JdbcTemplate jdbc, SourceSystemService sourceSystemService) {
        this.jdbc = jdbc;
        this.sourceSystemService = sourceSystemService;
    }

    /**
     * Attempts to acquire the ingestion lock for the given source.
     * Non-blocking — returns false immediately if the lock is already held.
     *
     * @param sourceCode
     * @param lockedBy   identity of the lock requester
     * @param ttl        how long this lock is considered valid; prevents stale locks
     *                   from blocking forever if the holder crashes
     * @return true if the lock was acquired; false if it is currently held by another
     */
    public boolean tryAcquire(String sourceCode, String lockedBy, Duration ttl) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        Instant expiresAt = Instant.now().plus(ttl);

        /*
         * Atomically insert or update the lock row, but only update if the
         * existing row has expired. PostgreSQL returns 0 affected rows when
         * ON CONFLICT DO UPDATE WHERE is false, the lock is still valid.
         */
        int affected = jdbc.update("""
                INSERT INTO fdp_core.ingestion_lock (source_system_id, locked_at, locked_by, expires_at)
                VALUES (?, now(), ?, ?)
                ON CONFLICT (source_system_id) DO UPDATE
                  SET locked_at  = EXCLUDED.locked_at,
                      locked_by  = EXCLUDED.locked_by,
                      expires_at = EXCLUDED.expires_at
                  WHERE fdp_core.ingestion_lock.expires_at < now()
                """,
                sourceSystemId, lockedBy, expiresAt
        );

        boolean acquired = affected > 0;
        if (acquired) {
            log.info("[lock] Acquired — source={}, lockedBy={}, ttl={}", sourceCode, lockedBy, ttl);
        } else {
            log.warn("[lock] Could not acquire — source={} is locked by another process", sourceCode);
        }
        return acquired;
    }

    /**
     * Releases the ingestion lock for the given source if it is owned by lockedBy.
     * Safe to call even if the lock has already expired or was stolen — the DELETE
     * is a no-op in those cases.
     *
     * @param sourceCode
     * @param lockedBy   must match the locked_by column value to prevent foreign release
     */
    public void release(String sourceCode, String lockedBy) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        int deleted = jdbc.update("""
                DELETE FROM fdp_core.ingestion_lock
                WHERE source_system_id = ? AND locked_by = ?
                """,
                sourceSystemId, lockedBy
        );
        if (deleted > 0) {
            log.info("[lock] Released — source={}, lockedBy={}", sourceCode, lockedBy);
        } else {
            log.warn("[lock] Release no-op — lock for source={} was not held by {}", sourceCode, lockedBy);
        }
    }
}
