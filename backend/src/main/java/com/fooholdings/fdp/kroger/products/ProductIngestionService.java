package com.fooholdings.fdp.kroger.products;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.ingestion.IngestionRunService;
import com.fooholdings.fdp.kroger.KrogerApiClient;
import com.fooholdings.fdp.kroger.prices.PriceObservationEntity;
import com.fooholdings.fdp.kroger.prices.PriceObservationRepository;
import com.fooholdings.fdp.kroger.products.dto.KrogerProductsResponse;

@Service
public class ProductIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ProductIngestionService.class);

    private final KrogerApiClient krogerApiClient;
    private final ProductCatalogRepository productRepo;
    private final PriceObservationRepository priceRepo;
    private final IngestionRunService runService;

    public ProductIngestionService(KrogerApiClient krogerApiClient,
                                   ProductCatalogRepository productRepo,
                                   PriceObservationRepository priceRepo,
                                    IngestionRunService runService) {
        this.krogerApiClient = krogerApiClient;
        this.productRepo = productRepo;
        this.priceRepo = priceRepo;
        this.runService = runService;
    }

    public IngestResult ingest(String locationId, String term, int limit, int start) {

        Long runId = runService.startRun(
                "kroger.products",
                "locationId=" + locationId + ", term=" + term + ", start=" + start + ", limit=" + limit
        );

        try {
            KrogerProductsResponse resp = krogerApiClient.searchProducts(locationId, term, limit, start);
            List<KrogerProductsResponse.Product> products =
                    (resp == null || resp.getData() == null) ? List.of() : resp.getData();

            log.info("Kroger products fetched: locationId={}, term='{}', start={}, limit={}, count={}",
                    locationId, term, start, limit, products.size());

            if (products.isEmpty()) {
                IngestResult result = new IngestResult(locationId, term, start, limit, 0, 0, 0);
                runService.endSuccess(runId, "fetched=0, productsSaved=0, priceRowsSaved=0");
                return result;
            }

            Instant now = Instant.now();
            List<ProductCatalogEntity> productEntities = new ArrayList<>();
            List<PriceObservationEntity> priceEntities = new ArrayList<>();

            for (KrogerProductsResponse.Product p : products) {
                if (p == null || p.getProductId() == null || p.getProductId().isBlank()) continue;

                ProductCatalogEntity e = new ProductCatalogEntity();
                e.setProductId(p.getProductId());
                e.setUpc(p.getUpc());
                e.setDescription(p.getDescription());

                productRepo.findById(e.getProductId()).ifPresentOrElse(existing -> {
                    e.setCreatedAt(existing.getCreatedAt());
                }, () -> {
                    e.setCreatedAt(now);
                });
                e.setUpdatedAt(now);
                productEntities.add(e);

                BigDecimal regular = null;
                BigDecimal promo = null;

                if (p.getItems() != null && !p.getItems().isEmpty() && p.getItems().get(0) != null) {
                    var price = p.getItems().get(0).getPrice();
                    if (price != null) {
                        if (price.getRegular() != null) regular = BigDecimal.valueOf(price.getRegular());
                        if (price.getPromo() != null) promo = BigDecimal.valueOf(price.getPromo());
                    }
                }

                PriceObservationEntity po = new PriceObservationEntity();
                po.setProductId(p.getProductId());
                po.setLocationId(locationId);
                po.setRegularPrice(regular);
                po.setPromoPrice(promo);
                po.setObservedAt(now);
                priceEntities.add(po);
            }

            productRepo.saveAll(productEntities);
            priceRepo.saveAll(priceEntities);

            IngestResult result = new IngestResult(locationId, term, start, limit,
                    products.size(),
                    productEntities.size(),
                    priceEntities.size());

            runService.endSuccess(runId,
                    "fetched=" + result.fetched()
                            + ", productsSaved=" + result.productsSaved()
                            + ", priceRowsSaved=" + result.priceRowsSaved()
            );

            return result;

        } catch (Exception ex) {
            runService.endFailure(runId, ex);
            throw ex;
        }
    }

    public record IngestResult(
            String locationId,
            String term,
            int start,
            int limit,
            int fetched,
            int productsSaved,
            int priceRowsSaved
    ) {}
}
