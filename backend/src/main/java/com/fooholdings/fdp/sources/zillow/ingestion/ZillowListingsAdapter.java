package com.fooholdings.fdp.sources.zillow.ingestion;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.config.GeoLevelGate;
import com.fooholdings.fdp.geo.repo.AreaSnapshotJdbcRepository;
import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;
import com.fooholdings.fdp.sources.zillow.csv.ZillowCsvFetcher;
import com.fooholdings.fdp.sources.zillow.csv.ZillowMetricFileSpec;

@Component
public class ZillowListingsAdapter extends AbstractZillowAdapter {

    public ZillowListingsAdapter(
            ZillowCsvFetcher fetcher,
            ZillowGeoResolver resolver,
            AreaSnapshotJdbcRepository snapshotRepo,
            GeoLevelGate geoLevelGate,
            ZillowProperties props,
            ApplicationEventPublisher eventPublisher
    ) {
        super(fetcher, resolver, snapshotRepo, geoLevelGate, props, eventPublisher);
    }

    public int ingest() {
        return ingest(List.of(
                ZillowMetricFileSpec.LISTINGS_INVENTORY_METRO,
                ZillowMetricFileSpec.LISTINGS_MEDIAN_LIST_PRICE_METRO,
                ZillowMetricFileSpec.LISTINGS_NEW_LISTINGS_METRO,
                ZillowMetricFileSpec.LISTINGS_NEW_PENDING_METRO,
                ZillowMetricFileSpec.SALES_MEDIAN_SALE_PRICE_METRO,
                ZillowMetricFileSpec.SALES_COUNT_METRO,
                ZillowMetricFileSpec.DAYS_TO_PENDING_METRO,
                ZillowMetricFileSpec.PRICE_CUT_SHARE_METRO,
                ZillowMetricFileSpec.MARKET_HEAT_METRO
        ));
    }
}