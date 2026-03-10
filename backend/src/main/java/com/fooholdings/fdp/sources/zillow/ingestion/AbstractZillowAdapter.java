package com.fooholdings.fdp.sources.zillow.ingestion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fooholdings.fdp.geo.config.GeoLevelGate;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository.AreaSnapshotUpsert;
import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvFetcher;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvRecord;
import com.fooholdings.fdp.sources.zillow.csv.ZillowMetricFileSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractZillowAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final ZillowCsvFetcher fetcher;
    private final ZillowGeoResolver resolver;
    private final AreaSnapshotJdbcRepository snapshotRepo;
    private final GeoLevelGate geoLevelGate;
    private final ZillowProperties props;

    protected AbstractZillowAdapter(
            ZillowCsvFetcher fetcher,
            ZillowGeoResolver resolver,
            AreaSnapshotJdbcRepository snapshotRepo,
            GeoLevelGate geoLevelGate,
            ZillowProperties props
    ) {
        this.fetcher = fetcher;
        this.resolver = resolver;
        this.snapshotRepo = snapshotRepo;
        this.geoLevelGate = geoLevelGate;
        this.props = props;
    }

    public int ingest(List<ZillowMetricFileSpec> specs) {
        int totalWritten = 0;

        for (ZillowMetricFileSpec spec : specs) {
            totalWritten += ingestFile(spec);
        }

        return totalWritten;
    }

    protected int ingestFile(ZillowMetricFileSpec spec) {
        List<ZillowCsvRecord> rows = fetcher.fetch(spec);
        List<AreaSnapshotUpsert> batch = new ArrayList<>();
        int totalWritten = 0;
        int batchSize = Math.max(props.batchSize(), 100);

        for (ZillowCsvRecord row : rows) {
            String level = mapRegionType(row.regionType());
            if (level == null || !geoLevelGate.isEnabled(level)) {
                continue;
            }

            UUID geoId = resolver.resolve(row).orElse(null);
            if (geoId == null) {
                log.warn("Unmapped Zillow row: spec={}, regionId={}, regionType={}, regionName={}",
                        spec.name(), row.regionId(), row.regionType(), row.regionName());
                continue;
            }

            for (Map.Entry<LocalDate, Double> valueEntry : row.values().entrySet()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("value", valueEntry.getValue());
                payload.put("regionId", row.regionId());
                payload.put("regionName", row.regionName());
                payload.put("regionType", row.regionType());

                batch.add(new AreaSnapshotUpsert(
                        geoId,
                        spec.category(),
                        valueEntry.getKey(),
                        spec.sourceName(),
                        payload
                ));

                if (batch.size() >= batchSize) {
                    totalWritten += snapshotRepo.batchUpsert(batch);
                    batch.clear();
                }
            }
        }

        if (!batch.isEmpty()) {
            totalWritten += snapshotRepo.batchUpsert(batch);
        }

        return totalWritten;
    }

    protected String mapRegionType(String regionType) {
        if (regionType == null) {
            return null;
        }

        return switch (regionType.trim().toLowerCase()) {
            case "country", "national" -> "national";
            case "state" -> "state";
            case "msa", "metro" -> "metro";
            case "county" -> "county";
            case "city" -> "city";
            case "zip", "zipcode" -> "zip";
            default -> null;
        };
    }
}