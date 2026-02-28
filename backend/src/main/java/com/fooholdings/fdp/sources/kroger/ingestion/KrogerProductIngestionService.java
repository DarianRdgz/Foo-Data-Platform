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
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationRepository;
import com.fooholdings.fdp.grocery.price.PriceObservationJdbcRepository;
import com.fooholdings.fdp.grocery.price.PriceObservationJdbcRepository.PriceObservationRow;
import com.fooholdings.fdp.grocery.product.SourceProductJdbcRepository;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
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
    private static final String LOCKED_BY = "fdp-backend";
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
     * Ingests products and price observations for the given location IDs and search terms.
     *
     * @param locationIds  Kroger location IDs (source_location_id values from store_location)
     * @param searchTerms  product search terms to query (e.g. ["milk", "bread", "eggs"])
     * @return summary message from the completed run
     */
    public String ingest(List<String> locationIds, List<String> searchTerms) {
        if (!lockService.tryAcquire(SOURCE_CODE, LOCKED_BY, LOCK_TTL)) {
            throw new IllegalStateException(
                    "Kroger product ingestion is already running. Try again later.");
        }

        UUID runId = null;
        try {
            runId = runService.startRun(
                    SOURCE_CODE,
                    Map.of("type", "products", "locationIds", locationIds, "searchTerms", searchTerms),
                    LOCKED_BY
            );

            short sourceSystemId = sourceSystemService.getRequiredIdByCode(SOURCE_CODE);
            List<PriceObservationRow> priceRows = new ArrayList<>();
            int productCount = 0;
            Instant observedAt = Instant.now(); // All observations in this run share the same timestamp

            for (String locationId : locationIds) {
                // Look up the internal UUID for this Kroger location
                var locationEntity = locationRepo
                        .findBySourceSystemIdAndSourceLocationId(sourceSystemId, locationId)
                        .orElse(null);

                if (locationEntity == null) {
                    log.warn("[run:{}] Skipping locationId={} — not found in store_location. " +
                             "Run location ingestion first.", runId, locationId);
                    continue;
                }

                UUID storeLocationPk = locationEntity.getId();

                for (String term : searchTerms) {
                    log.debug("[run:{}] Fetching products — location={}, term={}", runId, locationId, term);

                    KrogerProductsResponse response = apiClient.getProducts(locationId, term, runId);
                    List<Product> products = response.getData();
                    if (products == null || products.isEmpty()) continue;

                    for (Product product : products) {
                        productCount++;

                        // Upsert source_product and get the stable internal UUID
                        UUID sourceProductPk = sourceProductRepo.upsert(
                                UUID.randomUUID(),
                                sourceSystemId,
                                product.getProductId(),
                                product.getUpc(),
                                product.getDescription(),
                                null,  // brand not in current DTO
                                null,  // product_page_uri not in current DTO
                                null,  // raw_category_json: extend when Kroger DTO has categories
                                null   // raw_flags_json: extend when Kroger DTO has flags
                        );

                        // Extract price from the first item (Kroger products have 1+ items/sizes)
                        Price price = extractFirstPrice(product);
                        if (price == null) continue;

                        BigDecimal regular = price.getRegular() != null
                                ? BigDecimal.valueOf(price.getRegular()) : null;
                        BigDecimal promo = price.getPromo() != null
                                ? BigDecimal.valueOf(price.getPromo()) : null;

                        priceRows.add(new PriceObservationRow(
                                sourceSystemId,
                                storeLocationPk,
                                sourceProductPk,
                                observedAt,
                                CURRENCY_CODE,
                                promo != null ? promo : regular,  // effective price
                                regular,
                                promo,
                                promo != null && regular != null && promo.compareTo(regular) < 0,
                                runId,
                                null  // rawPayloadId: could link here but the API call is 1:many, skip for now
                        ));
                    }
                }
            }

            int inserted = priceObsRepo.batchInsert(priceRows);
            String summary = String.format(
                    "Products: %d processed, %d/%d price observations inserted (remainder duplicates)",
                    productCount, inserted, priceRows.size());
            runService.finishSuccess(runId, summary);
            return summary;

        } catch (Exception e) {
            if (runId != null) runService.finishFailure(runId, e);
            throw e;
        } finally {
            lockService.release(SOURCE_CODE, LOCKED_BY);
        }
    }

    private static Price extractFirstPrice(Product product) {
        if (product.getItems() == null || product.getItems().isEmpty()) return null;
        Item first = product.getItems().get(0);
        return first != null ? first.getPrice() : null;
    }
}
