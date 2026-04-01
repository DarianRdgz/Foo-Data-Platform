package com.fooholdings.fdp.sources.noaa.ingestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.noaa.client.NoaaStormEventsClient;

/**
 * Aggregates NOAA Storm Events records into area_snapshot rows.
 *
 * Geo levels written: state and county only (per charter).
 *
 * County geo resolution strategy:
 *   1. Primary: five-digit FIPS (most records have this)
 *   2. Fallback: county name + state FIPS (for rows where FIPS is "000")
 *   3. Skip with debug log if both fail (NOAA zone names are noisy)
 *
 * Categories written:
 *   risk.disaster.noaa
 */
@Component
public class NoaaStormAdapter {

    private static final Logger log = LoggerFactory.getLogger(NoaaStormAdapter.class);

    static final String SOURCE   = "NOAA";
    static final String CATEGORY = "risk.disaster.noaa";

    private final NoaaStormEventsClient client;
    private final GeoAreaJdbcRepository geoRepo;
    private final AreaSnapshotJdbcRepository snapshotRepo;

    public NoaaStormAdapter(NoaaStormEventsClient client,
                            GeoAreaJdbcRepository geoRepo,
                            AreaSnapshotJdbcRepository snapshotRepo) {
        this.client = client;
        this.geoRepo = geoRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public int ingest() {
        List<NoaaStormEventRecord> records = client.fetchRecentEvents();
        LocalDate snapshotPeriod = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        rows.addAll(buildStateRows(records, snapshotPeriod));
        rows.addAll(buildCountyRows(records, snapshotPeriod));

        int written = snapshotRepo.batchUpsert(deduplicate(rows));
        log.info("NOAA: wrote {} area_snapshot rows", written);
        return written;
    }

    // ── State aggregation ─────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildStateRows(List<NoaaStormEventRecord> records,
                                                     LocalDate period) {
        Map<String, List<NoaaStormEventRecord>> byState = records.stream()
                .collect(Collectors.groupingBy(NoaaStormEventRecord::stateFips2));

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        for (Map.Entry<String, List<NoaaStormEventRecord>> e : byState.entrySet()) {
            Optional<UUID> geoId = geoRepo.findStateGeoIdByFips(e.getKey());
            if (geoId.isEmpty()) {
                log.warn("NOAA: no geo_id for state fips='{}' — skipping", e.getKey());
                continue;
            }
            rows.add(buildRow(geoId.get(), "state", e.getValue(), period));
        }
        return rows;
    }

    // ── County aggregation ────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildCountyRows(List<NoaaStormEventRecord> records,
                                                      LocalDate period) {
        Map<String, List<NoaaStormEventRecord>> byCounty = records.stream()
                .collect(Collectors.groupingBy(NoaaStormEventRecord::countyFips5));

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int fipsMiss = 0, nameMiss = 0;

        for (Map.Entry<String, List<NoaaStormEventRecord>> e : byCounty.entrySet()) {
            Optional<UUID> geoId = geoRepo.findCountyGeoIdByFips(e.getKey());

            if (geoId.isEmpty()) {
                // Fallback: use county name from first record in the group
                NoaaStormEventRecord sample = e.getValue().get(0);
                if (sample.countyName() != null) {
                    geoId = geoRepo.findCountyGeoIdByNameAndStateFips(
                            sample.countyName(), sample.stateFips2());
                }
                if (geoId.isEmpty()) {
                    log.debug("NOAA: no geo_id for county fips='{}' name='{}' — skipping",
                              e.getKey(), e.getValue().get(0).countyName());
                    fipsMiss++;
                    continue;
                }
                nameMiss++; // resolved via name fallback — count separately for diagnostics
            }

            rows.add(buildRow(geoId.get(), "county", e.getValue(), period));
        }

        log.info("NOAA: county aggregation — {} written, {} fips-miss ({}  resolved by name), {} unresolved",
                 byCounty.size() - fipsMiss, fipsMiss + nameMiss, nameMiss, fipsMiss);
        return rows;
    }

    // ── Row builder ───────────────────────────────────────────────────────

    private static AreaSnapshotUpsert buildRow(UUID geoId, String geoLevel,
                                               List<NoaaStormEventRecord> records,
                                               LocalDate period) {
        double totalDamage = records.stream().mapToDouble(NoaaStormEventRecord::estimatedDamageUsd).sum();
        int totalDeaths    = records.stream().mapToInt(NoaaStormEventRecord::deaths).sum();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stormEventsCount", records.size());
        payload.put("estimatedDamageUsd", totalDamage);
        payload.put("fatalities", totalDeaths);
        payload.put("frequency", "annual");
        payload.put("geoLevel", geoLevel);

        return new AreaSnapshotUpsert(geoId, CATEGORY, period, SOURCE, payload);
    }

    private static List<AreaSnapshotUpsert> deduplicate(List<AreaSnapshotUpsert> rows) {
        Map<String, AreaSnapshotUpsert> seen = new LinkedHashMap<>();
        for (AreaSnapshotUpsert r : rows) {
            seen.put(r.geoId() + "|" + r.category() + "|" + r.snapshotPeriod() + "|" + r.source(), r);
        }
        return new ArrayList<>(seen.values());
    }
}