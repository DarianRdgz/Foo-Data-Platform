package com.fooholdings.fdp.sources.fred.ingestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.fred.client.FredClient;
import com.fooholdings.fdp.sources.fred.client.FredClient.FredObservation;
import com.fooholdings.fdp.sources.fred.client.FredClient.FredSeriesMeta;

/**
 * Maps FRED series observations into area_snapshot upserts.
 *
 * Incremental behaviour:
 *   On the first run for a series, all available history is fetched.
 *   On subsequent runs, only observations newer than the last stored
 *   snapshot_period are fetched, making re-runs cheap.
 *
 * Idempotency:
 *   area_snapshot has a unique index on (geo_id, category, snapshot_period, source),
 *   so writing the same observation twice is a no-op update (same payload).
 */
@Component
public class FredAdapter {

    private static final Logger log = LoggerFactory.getLogger(FredAdapter.class);
    static final String SOURCE = "FRED";
    private static final LocalDate FRED_EPOCH = LocalDate.of(1776, 1, 1);

    private final FredClient client;
    private final FredSeriesCatalog catalog;
    private final GeoAreaJdbcRepository geoRepo;
    private final AreaSnapshotJdbcRepository snapshotRepo;
    private final JdbcTemplate jdbc;

    public FredAdapter(FredClient client, FredSeriesCatalog catalog,
                       GeoAreaJdbcRepository geoRepo, AreaSnapshotJdbcRepository snapshotRepo,
                       JdbcTemplate jdbc) {
        this.client      = client;
        this.catalog     = catalog;
        this.geoRepo     = geoRepo;
        this.snapshotRepo = snapshotRepo;
        this.jdbc        = jdbc;
    }

    public int ingest() {
        int totalWritten = 0;
        for (FredSeriesDefinition def : catalog.allEnabled()) {
            totalWritten += ingestSeries(def);
        }
        return totalWritten;
    }

    private int ingestSeries(FredSeriesDefinition def) {
        Optional<UUID> geoId = geoRepo.findGeoId(def.geoLevel(), def.geoKeyType(), def.geoKey());
        if (geoId.isEmpty()) {
            log.warn("FRED: no geo_id for seriesId={} geoLevel={} geoKeyType={} geoKey={} — skipping",
                     def.seriesId(), def.geoLevel(), def.geoKeyType(), def.geoKey());
            return 0;
        }

        LocalDate incrementalStart = lastObservationDate(geoId.get(), def.category())
                .map(d -> d.plusDays(1))   // exclusive: fetch only new observations
                .orElse(FRED_EPOCH);

        FredSeriesMeta meta;
        try {
            meta = client.getSeries(def.seriesId());
        } catch (Exception e) {
            log.error("FRED: failed to fetch metadata for seriesId={}: {}", def.seriesId(), e.getMessage());
            return 0;
        }

        List<FredObservation> observations;
        try {
            observations = client.getObservations(def.seriesId(), incrementalStart);
        } catch (Exception e) {
            log.error("FRED: failed to fetch observations for seriesId={}: {}", def.seriesId(), e.getMessage());
            return 0;
        }

        List<AreaSnapshotUpsert> batch = new ArrayList<>(observations.size());
        for (FredObservation obs : observations) {
            LocalDate period;
            try {
                period = LocalDate.parse(obs.date());
            } catch (Exception e) {
                log.warn("FRED: unparseable date '{}' for seriesId={} — skipping", obs.date(), def.seriesId());
                continue;
            }

            double value;
            try {
                value = Double.parseDouble(obs.value());
            } catch (NumberFormatException e) {
                continue; // already filtered "." in FredClient, but defensive
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("value",              value);
            payload.put("seriesId",           def.seriesId());
            payload.put("seriesTitle",        meta.title());
            payload.put("units",              meta.units());
            payload.put("frequency",          meta.frequency());
            payload.put("seasonalAdjustment", meta.seasonal_adjustment());
            payload.put("vintageDate",        obs.realtime_end());
            payload.put("observationDate",    obs.date());

            batch.add(new AreaSnapshotUpsert(geoId.get(), def.category(), period, SOURCE, payload));
        }

        int written = snapshotRepo.batchUpsert(batch);
        log.info("FRED: seriesId={} wrote {} rows (incremental from {})",
                 def.seriesId(), written, incrementalStart);
        return written;
    }

    /**
     * Returns the most recent snapshot_period stored for this series + geo pair.
     * Used to calculate the incremental observation_start date.
     */
    private Optional<LocalDate> lastObservationDate(UUID geoId, String category) {
        return Optional.ofNullable(jdbc.query(
                """
                select max(snapshot_period)
                from fdp_geo.area_snapshot
                where geo_id   = ?
                  and category = ?
                  and source   = ?
                """,
                rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null,
                geoId, category, SOURCE
        ));
    }
}