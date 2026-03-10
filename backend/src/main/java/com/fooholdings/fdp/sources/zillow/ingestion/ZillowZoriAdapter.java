package com.fooholdings.fdp.sources.zillow.ingestion;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.config.GeoLevelGate;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvFetcher;
import com.fooholdings.fdp.sources.zillow.csv.ZillowMetricFileSpec;

@Component
public class ZillowZoriAdapter extends AbstractZillowAdapter {

    public ZillowZoriAdapter(
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
                ZillowMetricFileSpec.ZORI_METRO,
                ZillowMetricFileSpec.ZORI_COUNTY,
                ZillowMetricFileSpec.ZORI_CITY,
                ZillowMetricFileSpec.ZORI_ZIP
        ));
    }
}