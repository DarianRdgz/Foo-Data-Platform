package com.fooholdings.fdp.sources.kroger.ingestion;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.core.logging.ErrorCategory;
import com.fooholdings.fdp.core.logging.ErrorCategoryMdc;
import com.fooholdings.fdp.core.logging.IngestionMdc;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationRepository;
import com.fooholdings.fdp.grocery.price.PriceObservationJdbcRepository;
import com.fooholdings.fdp.grocery.price.PriceObservationJdbcRepository.PriceObservationRow;
import com.fooholdings.fdp.grocery.product.SourceProductJdbcRepository;
import com.fooholdings.fdp.sources.adapter.GrocerySourceAdapter;
import com.fooholdings.fdp.sources.adapter.GrocerySourceAdapterRegistry;
import com.fooholdings.fdp.sources.model.CanonicalPriceObservation;
import com.fooholdings.fdp.sources.model.CanonicalProduct;
import com.fooholdings.fdp.sources.model.CanonicalProductPrice;
import com.fooholdings.fdp.sources.model.ProductQuery;
import com.fooholdings.fdp.sources.model.SourceType;

/**
 * Orchestrates the full lifecycle of a Kroger product + price ingestion run.
 */
@Service
public class KrogerProductIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KrogerProductIngestionService.class);
    private static final String SOURCE_CODE = "KROGER";
    private static final String DEFAULT_LOCKED_BY = "fdp-backend";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final GrocerySourceAdapter adapter;
    private final SourceProductJdbcRepository sourceProductRepo;
    private final PriceObservationJdbcRepository priceObsRepo;
    private final StoreLocationRepository locationRepo;
    private final IngestionRunService runService;
    private final IngestionLockService lockService;
    private final SourceSystemService sourceSystemService;

    public KrogerProductIngestionService(GrocerySourceAdapterRegistry adapterRegistry,
                                         SourceProductJdbcRepository sourceProductRepo,
                                         PriceObservationJdbcRepository priceObsRepo,
                                         StoreLocationRepository locationRepo,
                                         IngestionRunService runService,
                                         IngestionLockService lockService,
                                         SourceSystemService sourceSystemService) {
        this.adapter = adapterRegistry.getRequired(SourceType.KROGER);
        this.sourceProductRepo = sourceProductRepo;
        this.priceObsRepo = priceObsRepo;
        this.locationRepo = locationRepo;
        this.runService = runService;
        this.lockService = lockService;
        this.sourceSystemService = sourceSystemService;
    }

    public String ingest(List<String> locationIds, List<String> searchTerms) {
        return ingest(locationIds, searchTerms, DEFAULT_LOCKED_BY);
    }

    public String ingest(List<String> locationIds, List<String> searchTerms, String lockedBy) {
        if (!lockService.tryAcquire(SOURCE_CODE, lockedBy, LOCK_TTL)) {
            throw new IngestionLockException("Kroger product ingestion is already running. Try again later.");
        }

        UUID runId = null;
        try {
            runId = runService.startRun(
                    SOURCE_CODE,
                    Map.of("type", "products", "locationIds", locationIds, "searchTerms", searchTerms),
                    lockedBy
            );

            try (@SuppressWarnings("unused") var ctx = IngestionMdc.withRun(runId, SOURCE_CODE)) {

                short sourceSystemId = sourceSystemService.getRequiredIdByCode(SOURCE_CODE);

                log.info("Ingestion started: products (locations={}, terms={})",
                        (locationIds == null ? 0 : locationIds.size()),
                        (searchTerms == null ? 0 : searchTerms.size()));

                ProductQuery query = new ProductQuery(locationIds, searchTerms, runId);
                List<CanonicalProductPrice> results = adapter.fetchProducts(query);

                List<PriceObservationRow> priceRows = new ArrayList<>();
                int productCount = 0;

                for (CanonicalProductPrice cpp : results) {
                    if (cpp == null) continue;

                    String sourceLocationId = cpp.sourceLocationId();
                    var locationEntity = locationRepo
                            .findBySourceSystemIdAndSourceLocationId(sourceSystemId, sourceLocationId)
                            .orElse(null);

                    if (locationEntity == null) {
                        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
                            log.warn("Skipping sourceLocationId={} — not found in store_location. Run location ingestion first.",
                                    sourceLocationId);
                        }
                        continue;
                    }

                    UUID storeLocationPk = locationEntity.getId();

                    CanonicalProduct p = cpp.product();
                    if (p == null) continue;
                    productCount++;

                    UUID sourceProductPk = sourceProductRepo.upsert(
                            UUID.randomUUID(),
                            sourceSystemId,
                            p.sourceProductId(),
                            p.upc(),
                            p.name(),
                            p.brand(),
                            p.categories(),
                            p.productPageUri(),
                            p.rawCategoryJson(),
                            p.rawFlagsJson()
                    );

                    CanonicalPriceObservation obs = cpp.priceObservation();
                    if (obs == null || obs.price() == null) continue;

                    priceRows.add(new PriceObservationRow(
                            sourceSystemId,
                            storeLocationPk,
                            sourceProductPk,
                            obs.observedAt(),
                            obs.currencyCode(),
                            obs.price(),
                            obs.regularPrice(),
                            obs.promoPrice(),
                            Boolean.TRUE.equals(obs.isOnSale()),
                            runId,
                            null
                    ));
                }

                int inserted = priceObsRepo.batchInsert(priceRows);

                String summary = String.format(
                        "Products: %d processed, %d/%d price observations inserted (remainder duplicates)",
                        productCount, inserted, priceRows.size()
                );

                runService.finishSuccess(runId, inserted, summary);
                log.info("Ingestion completed: {}", summary);
                return summary;
            }

        } catch (Exception e) {
            if (runId != null) runService.finishFailure(runId, e);

            ErrorCategory category = classify(e);
            try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(category)) {
                if (category == ErrorCategory.LOCK_ERROR || category == ErrorCategory.VALIDATION_ERROR) {
                    log.warn("Ingestion failed: {}", e.getMessage());
                } else {
                    log.error("Ingestion failed: {}", e.getMessage(), e);
                }
            }
            throw e;

        } finally {
            lockService.release(SOURCE_CODE, lockedBy);
        }
    }

    private static ErrorCategory classify(Throwable e) {
        if (e instanceof IngestionLockException) return ErrorCategory.LOCK_ERROR;
        if (e instanceof DataAccessException) return ErrorCategory.DB_ERROR;
        if (e instanceof IllegalArgumentException) return ErrorCategory.VALIDATION_ERROR;
        return ErrorCategory.UNCLASSIFIED;
    }
}