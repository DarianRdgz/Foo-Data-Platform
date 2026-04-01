package com.fooholdings.fdp.geo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;

import tools.jackson.databind.ObjectMapper;

/**
 * Computes the composite disaster risk score from FEMA and NOAA snapshots.
 *
 * Scoring formula (v1 — deterministic capped min/max, no percentiles):
 *
 *   femaCountScore  = min(100, disasterCountLast10yr * 2.5)
 *   noaaEventScore  = min(100, stormEventsCount * 2.0)
 *   noaaDamageScore = min(100, estimatedDamageUsd / 1_000_000 * 10)
 *   noaaFatalScore  = min(100, fatalities * 20)
 *
 *   riskScore = 0.35 * femaCountScore
 *             + 0.25 * noaaEventScore
 *             + 0.20 * noaaDamageScore
 *             + 0.20 * noaaFatalScore
 *
 * Tier mapping:
 *   < 25  → low
 *   < 50  → moderate
 *   < 75  → high
 *   >= 75 → severe
 *
 * Design constraint: a composite row is only written when BOTH FEMA and NOAA
 * have a latest snapshot for the same geo_id. Geos with only one source are
 * skipped to avoid misleading scores.
 */
@Service
public class DisasterRiskScoreService {

    private static final Logger log = LoggerFactory.getLogger(DisasterRiskScoreService.class);

    static final String SOURCE   = "DISASTER_RISK";
    static final String CATEGORY = "risk.composite";

    private final JdbcTemplate jdbc;
    private final AreaSnapshotJdbcRepository snapshotRepo;
    private final ObjectMapper objectMapper;

    public DisasterRiskScoreService(JdbcTemplate jdbc,
                                    AreaSnapshotJdbcRepository snapshotRepo,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.snapshotRepo = snapshotRepo;
        this.objectMapper = objectMapper;
    }

    /**
     * Recomputes composite risk scores for all geos that have at least one
     * FEMA or NOAA snapshot. Only writes a composite row when both sources
     * are present for the same geo_id.
     *
     * @return number of composite rows written or updated
     */
    public int recomputeAll() {
        Map<UUID, SourcePayloads> byGeo = loadLatestByGeo();

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int skippedMissingSource = 0;
        LocalDate period = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        for (Map.Entry<UUID, SourcePayloads> entry : byGeo.entrySet()) {
            SourcePayloads sp = entry.getValue();
            if (sp.fema == null || sp.noaa == null) {
                skippedMissingSource++;
                continue; // require both sources — see class javadoc
            }
            rows.add(buildCompositeRow(entry.getKey(), sp, period));
        }

        int written = snapshotRepo.batchUpsert(rows);
        log.info("DisasterRiskScore: {} composites written, {} geos skipped (missing one source)",
                 written, skippedMissingSource);
        return written;
    }

    // ── Data loading ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<UUID, SourcePayloads> loadLatestByGeo() {
        String sql = """
                with ranked as (
                    select geo_id,
                           source,
                           payload::text as payload_text,
                           row_number() over (
                               partition by geo_id, source
                               order by snapshot_period desc, ingested_at desc
                           ) as rn
                    from fdp_geo.area_snapshot
                    where source in ('FEMA', 'NOAA')
                )
                select geo_id, source, payload_text
                from ranked
                where rn = 1
                """;

        Map<UUID, SourcePayloads> result = new HashMap<>();

        jdbc.query(sql, rs -> {
            UUID geoId   = UUID.fromString(rs.getString("geo_id"));
            String source = rs.getString("source");
            String payloadText = rs.getString("payload_text");

            Map<String, Object> payload;
            try {
                payload = objectMapper.readValue(payloadText, Map.class);
            } catch (Exception e) {
                log.warn("DisasterRiskScore: failed to parse payload for geo_id={} source={}: {}",
                         geoId, source, e.getMessage());
                return;
            }

            SourcePayloads sp = result.computeIfAbsent(geoId, k -> new SourcePayloads());
            if ("FEMA".equals(source)) sp.fema = payload;
            if ("NOAA".equals(source)) sp.noaa = payload;
        });

        return result;
    }

    // ── Score computation ─────────────────────────────────────────────────

    private static AreaSnapshotUpsert buildCompositeRow(UUID geoId, SourcePayloads sp,
                                                         LocalDate period) {
        double femaCount    = numberVal(sp.fema, "disasterCountLast10yr");
        double noaaEvents   = numberVal(sp.noaa, "stormEventsCount");
        double noaaDamage   = numberVal(sp.noaa, "estimatedDamageUsd");
        double noaaFatal    = numberVal(sp.noaa, "fatalities");

        double femaCountScore  = Math.min(100, femaCount  * 2.5);
        double noaaEventScore  = Math.min(100, noaaEvents * 2.0);
        double noaaDamageScore = Math.min(100, noaaDamage / 1_000_000.0 * 10);
        double noaaFatalScore  = Math.min(100, noaaFatal  * 20);

        double riskScore = 0.35 * femaCountScore
                         + 0.25 * noaaEventScore
                         + 0.20 * noaaDamageScore
                         + 0.20 * noaaFatalScore;

        String tier = riskScore < 25  ? "low"
                    : riskScore < 50  ? "moderate"
                    : riskScore < 75  ? "high"
                    :                   "severe";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("riskScore",       Math.round(riskScore * 100.0) / 100.0);
        payload.put("tier",            tier);
        payload.put("femaCountScore",  Math.round(femaCountScore  * 100.0) / 100.0);
        payload.put("noaaEventScore",  Math.round(noaaEventScore  * 100.0) / 100.0);
        payload.put("noaaDamageScore", Math.round(noaaDamageScore * 100.0) / 100.0);
        payload.put("noaaFatalScore",  Math.round(noaaFatalScore  * 100.0) / 100.0);
        payload.put("scoringVersion",  "v1");

        return new AreaSnapshotUpsert(geoId, CATEGORY, period, SOURCE, payload);
    }

    private static double numberVal(Map<String, Object> m, String key) {
        if (m == null) return 0;
        Object v = m.get(key);
        if (v == null) return 0;
        try { return ((Number) v).doubleValue(); }
        catch (ClassCastException e) { return 0; }
    }

    // ── Inner types ───────────────────────────────────────────────────────

    private static class SourcePayloads {
        Map<String, Object> fema;
        Map<String, Object> noaa;
    }
}