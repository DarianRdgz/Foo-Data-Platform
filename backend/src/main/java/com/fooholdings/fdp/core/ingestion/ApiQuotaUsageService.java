package com.fooholdings.fdp.core.ingestion;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.source.SourceSystemService;

/**
 * Tracks daily API call counts against fdp_core.api_quota_usage.
 *
 * K rate limits (documented in FDP V1):
 *   - Locations API: 1,600 calls/day per endpoint
 *   - Products API:  10,000 calls/day
 *
 * This service increments the call count atomically using an upsert.
 * The count persisted in the DB.
 *
 * Active enforcement is a planned Story 4.3 addition.
 * When implemented, add a canCallEndpoint(source, endpoint, dailyLimit) method
 * that reads the current count and compares against the configured limit.
 *
 * Future: add a scheduled job to rotate/archive old rows (Story 4.4).
 */
@Service
public class ApiQuotaUsageService {

    private static final Logger log = LoggerFactory.getLogger(ApiQuotaUsageService.class);

    private final JdbcTemplate jdbc;
    private final SourceSystemService sourceSystemService;

    public ApiQuotaUsageService(JdbcTemplate jdbc, SourceSystemService sourceSystemService) {
        this.jdbc = jdbc;
        this.sourceSystemService = sourceSystemService;
    }

    /**
     * Atomically increments the call count for the given source + endpoint combination.
     * Uses INSERT ... ON CONFLICT DO UPDATE so the first call of the day creates the row
     * and subsequent calls increment it without any read-before-write race condition.
     *
     * @param sourceCode
     * @param endpointKey short descriptor
     * @param amount      number of API calls made in this batch
     */
    public void increment(String sourceCode, String endpointKey, int amount) {
        short sourceSystemId = sourceSystemService.getRequiredIdByCode(sourceCode);
        LocalDate today = LocalDate.now();

        jdbc.update("""
                INSERT INTO fdp_core.api_quota_usage (source_system_id, usage_date, endpoint, call_count, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (source_system_id, usage_date, endpoint) DO UPDATE
                  SET call_count = fdp_core.api_quota_usage.call_count + EXCLUDED.call_count,
                      updated_at = now()
                """,
                sourceSystemId, today, endpointKey, amount
        );

        log.debug("[quota] +{} call(s) — source={}, endpoint={}, date={}", amount, sourceCode, endpointKey, today);
    }
}
