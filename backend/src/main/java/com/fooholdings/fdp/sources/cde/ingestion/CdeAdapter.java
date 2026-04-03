package com.fooholdings.fdp.sources.cde.ingestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;
import com.fooholdings.fdp.sources.cde.artifact.CdeArtifact;
import com.fooholdings.fdp.sources.cde.parser.CdeArtifactParser;
import com.fooholdings.fdp.sources.cde.parser.CdeArtifactParser.CdeCrimeRow;

@Component
public class CdeAdapter {

    private static final Logger log = LoggerFactory.getLogger(CdeAdapter.class);

    static final String SOURCE                  = "CDE";
    static final String CATEGORY_VIOLENT        = "safety.crime.violent_rate";
    static final String CATEGORY_PROPERTY       = "safety.crime.property_rate";
    static final String CATEGORY_TOTAL_PER_100K = "safety.crime.total_incidents_per_100k";

    private final CdeArtifactParser parser;
    private final GeoAreaJdbcRepository geoRepo;
    private final AreaSnapshotJdbcRepository snapshotRepo;

    public CdeAdapter(CdeArtifactParser parser,
                      GeoAreaJdbcRepository geoRepo,
                      AreaSnapshotJdbcRepository snapshotRepo) {
        this.parser = parser;
        this.geoRepo = geoRepo;
        this.snapshotRepo = snapshotRepo;
    }

    public int ingestArtifact(CdeArtifact artifact) {
        List<CdeCrimeRow> rows = parser.parse(artifact);
        List<AreaSnapshotUpsert> upserts = mapRows(rows);
        int written = snapshotRepo.batchUpsert(deduplicate(upserts));
        log.info("CDE: wrote {} rows from artifact {}", written, artifact.originalFilename());
        return written;
    }

    private List<AreaSnapshotUpsert> mapRows(List<CdeCrimeRow> rows) {
        List<AreaSnapshotUpsert> out = new ArrayList<>();
        for (CdeCrimeRow row : rows) {
            Integer year = parseYear(row.year());
            if (year == null) {
                continue;
            }

            LocalDate snapshotPeriod = LocalDate.of(year, 1, 1);
            Optional<ResolvedGeo> geo = resolveGeo(row.state());
            if (geo.isEmpty()) {
                log.warn("CDE: skipping unmapped state='{}' year={}", row.state(), year);
                continue;
            }

            if (row.violentRate() != null) {
                out.add(build(geo.get(), CATEGORY_VIOLENT, snapshotPeriod, row.violentRate(), row));
            }
            if (row.propertyRate() != null) {
                out.add(build(geo.get(), CATEGORY_PROPERTY, snapshotPeriod, row.propertyRate(), row));
            }
            if (row.totalIncidentsPer100k() != null) {
                out.add(build(geo.get(), CATEGORY_TOTAL_PER_100K, snapshotPeriod, row.totalIncidentsPer100k(), row));
            }
        }
        return out;
    }

    private AreaSnapshotUpsert build(ResolvedGeo geo, String category, LocalDate period,
                                     double value, CdeCrimeRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("value", value);
        payload.put("units", "per_100k");
        payload.put("frequency", "annual");
        payload.put("year", parseYear(row.year()));
        payload.put("geoLevel", geo.geoLevel());
        payload.put("state", row.state());
        if (row.totalIncidents() != null) {
            payload.put("totalIncidents", row.totalIncidents());
        }
        return new AreaSnapshotUpsert(geo.geoId(), category, period, SOURCE, payload);
    }

    public Optional<ResolvedGeo> resolveGeo(String rawState) {
        if (!StringUtils.hasText(rawState)) {
            return Optional.empty();
        }
        String trimmedState = rawState.trim();
        if (trimmedState.equalsIgnoreCase("united states")
                || trimmedState.equalsIgnoreCase("national")
                || trimmedState.equalsIgnoreCase("us")) {
            return geoRepo.findNationalGeoId().map(id -> new ResolvedGeo(id, "national"));
        }
        return geoRepo.findStateGeoIdByName(trimmedState).map(id -> new ResolvedGeo(id, "state"));
    }

    private Integer parseYear(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<AreaSnapshotUpsert> deduplicate(List<AreaSnapshotUpsert> rows) {
        Map<String, AreaSnapshotUpsert> deduped = new LinkedHashMap<>();
        for (AreaSnapshotUpsert row : rows) {
            deduped.put(row.geoId() + "|" + row.category() + "|" + row.snapshotPeriod() + "|" + row.source(), row);
        }
        return new ArrayList<>(deduped.values());
    }

    public record ResolvedGeo(UUID geoId, String geoLevel) {}
}
