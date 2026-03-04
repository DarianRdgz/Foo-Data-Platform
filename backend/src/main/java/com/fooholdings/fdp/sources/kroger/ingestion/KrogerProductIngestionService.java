package com.fooholdings.fdp.sources.kroger.ingestion;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiException;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Item;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Price;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Product;

/**
 * Orchestrates the full lifecycle of a Kroger product + price ingestion run.
 *
 * Flow:
 *   1. Acquire distributed lock (separate lock key from location ingestion)
 *   2. Start ingestion_run record
 *   3. For each (locationId, searchTerm): call /products, upsert source_product, collect price rows
 *   4. Batch-insert price observations with ON CONFLICT DO NOTHING
 *   5. Mark run SUCCESS / FAILED
 *   6. Release lock (always)
 *
 * Warning:
 *   Location and product ingestion intentionally cannot run concurrently for the same source
 *
 * Price observations are collected in memory then batch-inserted at the end.
 */
@Service
public class KrogerProductIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KrogerProductIngestionService.class);
    private static final String SOURCE_CODE = "KROGER";
    private static final String DEFAULT_LOCKED_BY = "fdp-backend";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final String CURRENCY_CODE = "USD";

    private final KrogerApiClient apiClient;
    private final SourceProductJdbcRepository sourceProductRepo;
    private final PriceObservationJdbcRepository priceObsRepo;
    private final StoreLocationRepository locationRepo;
    private final IngestionRunService runService;
    private final IngestionLockService lockService;
    private final SourceSystemService sourceSystemService;

    public KrogerProductIngestionService(KrogerApiClient apiClient,
                                         SourceProductJdbcRepository sourceProductRepo,
                                         PriceObservationJdbcRepository priceObsRepo,
                                         StoreLocationRepository locationRepo,
                                         IngestionRunService runService,
                                         IngestionLockService lockService,
                                         SourceSystemService sourceSystemService) {
        this.apiClient = apiClient;
        this.sourceProductRepo = sourceProductRepo;
        this.priceObsRepo = priceObsRepo;
        this.locationRepo = locationRepo;
        this.runService = runService;
        this.lockService = lockService;
        this.sourceSystemService = sourceSystemService;
    }

    /**
     * Manual trigger entry point. Uses the default lockedBy value.
     *
     * @param locationIds  Kroger location IDs from store_location
     * @param searchTerms  product search terms to query
     * @return summary message from the completed run
     * @throws IngestionLockException if a run is already in progress
     */
    public String ingest(List<String> locationIds, List<String> searchTerms) {
        return ingest(locationIds, searchTerms, DEFAULT_LOCKED_BY);
    }

    /**
     * Full entry point — accepts a caller identity written to ingestion_run.locked_by.
     * Scheduled jobs pass lockedBy="scheduler"; manual triggers use the no-arg overload.
     *
     * @param locationIds  Kroger location IDs from store_location
     * @param searchTerms  product search terms to query
     * @param lockedBy     identity of the caller, recorded in ingestion_run
     * @return summary message from the completed run
     * @throws IngestionLockException if a run is already in progress
     */
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
                List<PriceObservationRow> priceRows = new ArrayList<>();
                int productCount = 0;
                Instant observedAt = Instant.now();

                log.info("Ingestion started: products (locations={}, terms={})", locationIds.size(), searchTerms.size());

                for (String locationId : locationIds) {
                    var locationEntity = locationRepo
                            .findBySourceSystemIdAndSourceLocationId(sourceSystemId, locationId)
                            .orElse(null);

                    if (locationEntity == null) {
                        try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.VALIDATION_ERROR)) {
                            log.warn("Skipping locationId={} — not found in store_location. Run location ingestion first.", locationId);
                        }
                        continue;
                    }

                    UUID storeLocationPk = locationEntity.getId();

                    for (String term : searchTerms) {
                        log.debug("Fetching products — location={}, term={}", locationId, term);

                        KrogerProductsResponse response = apiClient.getProducts(locationId, term, runId);
                        List<Product> products = response.getData();
                        if (products == null || products.isEmpty()) continue;

                        for (Product product : products) {
                            productCount++;

                            UUID sourceProductPk = sourceProductRepo.upsert(
                                UUID.randomUUID(),
                                sourceSystemId,
                                product.getProductId(),
                                product.getUpc(),
                                product.getDescription(),  // name/description
                                product.getBrand(),        // brand
                                toCategoriesArray(product.getCategories()), // categories (String[])
                                null,                      // product_page_uri
                                null,                      // raw_category_json 
                                null                       // raw_flags_json 
                        );

                            Price price = extractFirstPrice(product);
                            if (price == null) continue;

                            BigDecimal regular = price.getRegular() != null ? BigDecimal.valueOf(price.getRegular()) : null;
                            BigDecimal promo = price.getPromo() != null ? BigDecimal.valueOf(price.getPromo()) : null;

                            priceRows.add(new PriceObservationRow(
                                    sourceSystemId,
                                    storeLocationPk,
                                    sourceProductPk,
                                    observedAt,
                                    CURRENCY_CODE,
                                    promo != null ? promo : regular,
                                    regular,
                                    promo,
                                    promo != null && regular != null && promo.compareTo(regular) < 0,
                                    runId,
                                    null
                            ));
                        }
                    }
                }

                int inserted = priceObsRepo.batchInsert(priceRows);
                String summary = String.format(
                        "Products: %d processed, %d/%d price observations inserted (remainder duplicates)",
                        productCount, inserted, priceRows.size());
                runService.finishSuccess(runId, summary);

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
        if (e instanceof KrogerApiException) return ErrorCategory.API_ERROR;
        if (e instanceof DataAccessException) return ErrorCategory.DB_ERROR;
        if (e instanceof IllegalArgumentException) return ErrorCategory.VALIDATION_ERROR;
        return ErrorCategory.UNCLASSIFIED;
    }

    private static Price extractFirstPrice(Product product) {
        if (product.getItems() == null || product.getItems().isEmpty()) return null;
        Item first = product.getItems().get(0);
        return first != null ? first.getPrice() : null;
    }

    private static String[] toCategoriesArray(java.util.List<String> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
