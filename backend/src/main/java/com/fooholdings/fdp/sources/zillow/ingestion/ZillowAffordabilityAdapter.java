package com.fooholdings.fdp.sources.zillow.ingestion;

import java.util.List;

import com.fooholdings.fdp.geo.config.GeoLevelGate;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvFetcher;
import com.fooholdings.fdp.sources.zillow.csv.ZillowMetricFileSpec;
import org.springframework.stereotype.Component;

@Component
public class ZillowAffordabilityAdapter extends AbstractZillowAdapter {

    public ZillowAffordabilityAdapter(
            ZillowCsvFetcher fetcher,
            ZillowGeoResolver resolver,
            AreaSnapshotJdbcRepository snapshotRepo,
            GeoLevelGate geoLevelGate,
            ZillowProperties props
    ) {
        super(fetcher, resolver, snapshotRepo, geoLevelGate, props);
    }

    public int ingest() {
        return ingest(List.of(
                ZillowMetricFileSpec.AFFORDABILITY_INCOME_NEEDED_METRO,
                ZillowMetricFileSpec.AFFORDABILITY_RATIO_METRO,
                ZillowMetricFileSpec.AFFORDABILITY_YEARS_TO_SAVE_METRO
        ));
    }
}