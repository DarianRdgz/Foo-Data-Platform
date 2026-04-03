package com.fooholdings.fdp.sources.fema.ingestion;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.event.AreaIngestCompletedEvent;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.fema.client.FemaClient;


/**
 * Aggregates FEMA disaster declarations into area_snapshot rows.
 *
 * Geo levels written: state and county only (per charter).
 * Window: last 10 years relative to the run date (rolling).
 *
 * Categories written:
 *   risk.disaster.fema  (one row per geo per snapshot period)
 *
 * declarationsPer100k is intentionally null — population data is not available
 * in this codebase yet. To be added in 5.5/6 when a population source lands.
 */
@Component
public class FemaDisasterAdapter {

    private static final Logger log = LoggerFactory.getLogger(FemaDisasterAdapter.class);

    static final String SOURCE   = "FEMA";
    static final String CATEGORY = "risk.disaster.fema";
    static final int    WINDOW_YEARS = 10;

    private final FemaClient client;
    private final GeoAreaJdbcRepository geoRepo;
    private final AreaSnapshotJdbcRepository snapshotRepo;
    private final ApplicationEventPublisher eventPublisher;

    public FemaDisasterAdapter(FemaClient client,
                                GeoAreaJdbcRepository geoRepo,
                                AreaSnapshotJdbcRepository snapshotRepo,
                                ApplicationEventPublisher eventPublisher) {
        this.client = client;
        this.geoRepo = geoRepo;
        this.snapshotRepo = snapshotRepo;
        this.eventPublisher = eventPublisher;
    }

    public int ingest() {
        List<FemaDisasterDeclarationRecord> all = client.fetchDeclarations();
        LocalDate cutoff = LocalDate.now().minusYears(WINDOW_YEARS);
        LocalDate snapshotPeriod = LocalDate.of(LocalDate.now().getYear(), 12, 31);

        List<FemaDisasterDeclarationRecord> recent = all.stream()
                .filter(r -> parseDate(r.declarationDate()) != null)
                .filter(r -> !parseDate(r.declarationDate()).isBefore(cutoff))
                .toList();

        log.info("FEMA: {} declarations in last {} years (of {} total)",
                 recent.size(), WINDOW_YEARS, all.size());

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        rows.addAll(buildStateRows(recent, snapshotPeriod));
        rows.addAll(buildCountyRows(recent, snapshotPeriod));

        
        int written = snapshotRepo.batchUpsert(deduplicate(rows));

        if (written > 0) {
            eventPublisher.publishEvent(new AreaIngestCompletedEvent(this, SOURCE, null, written));
        }

        log.info("FEMA: wrote {} area_snapshot rows", written);
        return written;
    }

    // ── State aggregation ─────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildStateRows(List<FemaDisasterDeclarationRecord> records,
                                                     LocalDate period) {
        Map<String, List<FemaDisasterDeclarationRecord>> byState = records.stream()
                .collect(Collectors.groupingBy(FemaDisasterDeclarationRecord::stateFips2));

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int misses = 0;

        for (Map.Entry<String, List<FemaDisasterDeclarationRecord>> e : byState.entrySet()) {
            Optional<UUID> geoId = geoRepo.findStateGeoIdByFips(e.getKey());
            if (geoId.isEmpty()) {
                log.warn("FEMA: no geo_id for state fips='{}' — skipping", e.getKey());
                misses++;
                continue;
            }
            rows.add(buildRow(geoId.get(), "state", e.getValue(), period));
        }

        if (misses > 0) log.warn("FEMA: {} state FIPS codes had no geo_id match", misses);
        return rows;
    }

    // ── County aggregation ────────────────────────────────────────────────

    private List<AreaSnapshotUpsert> buildCountyRows(List<FemaDisasterDeclarationRecord> records,
                                                      LocalDate period) {
        Map<String, List<FemaDisasterDeclarationRecord>> byCounty = records.stream()
                .filter(r -> r.countyFips5() != null)
                .collect(Collectors.groupingBy(FemaDisasterDeclarationRecord::countyFips5));

        List<AreaSnapshotUpsert> rows = new ArrayList<>();
        int misses = 0;

        for (Map.Entry<String, List<FemaDisasterDeclarationRecord>> e : byCounty.entrySet()) {
            Optional<UUID> geoId = geoRepo.findCountyGeoIdByFips(e.getKey());
            if (geoId.isEmpty()) {
                log.debug("FEMA: no geo_id for county fips='{}' — skipping", e.getKey());
                misses++;
                continue;
            }
            rows.add(buildRow(geoId.get(), "county", e.getValue(), period));
        }

        log.info("FEMA: county aggregation — {} counties written, {} unmatched", byCounty.size() - misses, misses);
        return rows;
    }

    // ── Row builder ───────────────────────────────────────────────────────

    private static AreaSnapshotUpsert buildRow(UUID geoId, String geoLevel,
                                               List<FemaDisasterDeclarationRecord> records,
                                               LocalDate period) {
        String mostCommon = records.stream()
                .collect(Collectors.groupingBy(FemaDisasterDeclarationRecord::incidentType, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("disasterCountLast10yr", records.size());
        payload.put("mostCommonDisasterType", mostCommon);
        payload.put("declarationsPer100k", null);  // TODO: add when population source lands in 5.5/6
        payload.put("frequency", "annual");
        payload.put("windowYears", WINDOW_YEARS);
        payload.put("geoLevel", geoLevel);

        return new AreaSnapshotUpsert(geoId, CATEGORY, period, SOURCE, payload);
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // Handle ISO with and without time component
            return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw,
                                   DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<AreaSnapshotUpsert> deduplicate(List<AreaSnapshotUpsert> rows) {
        Map<String, AreaSnapshotUpsert> seen = new LinkedHashMap<>();
        for (AreaSnapshotUpsert r : rows) {
            seen.put(r.geoId() + "|" + r.category() + "|" + r.snapshotPeriod() + "|" + r.source(), r);
        }
        return new ArrayList<>(seen.values());
    }
}
