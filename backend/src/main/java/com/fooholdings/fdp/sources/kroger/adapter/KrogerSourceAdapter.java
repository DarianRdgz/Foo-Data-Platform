package com.fooholdings.fdp.sources.kroger.adapter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fooholdings.fdp.sources.adapter.GrocerySourceAdapter;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse.Location;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Item;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Price;
import com.fooholdings.fdp.sources.kroger.dto.products.KrogerProductsResponse.Product;
import com.fooholdings.fdp.sources.model.CanonicalLocation;
import com.fooholdings.fdp.sources.model.CanonicalPriceObservation;
import com.fooholdings.fdp.sources.model.CanonicalProduct;
import com.fooholdings.fdp.sources.model.CanonicalProductPrice;
import com.fooholdings.fdp.sources.model.LocationQuery;
import com.fooholdings.fdp.sources.model.ProductQuery;
import com.fooholdings.fdp.sources.model.SourceType;

/**
 *
 * Responsibilities:
 * - Call Kroger endpoints via KrogerApiClient
 * - Map Kroger DTOs -> Canonical DTOs
 *
 */
@Component
public class KrogerSourceAdapter implements GrocerySourceAdapter {

    private static final String COUNTRY_CODE = "US";
    private static final String CURRENCY_CODE = "USD";

    private final KrogerApiClient apiClient;

    public KrogerSourceAdapter(KrogerApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.KROGER;
    }

    @Override
    public List<CanonicalLocation> fetchLocations(LocationQuery query) {
        if (query == null) return List.of();

        List<String> zipCodes = query.zipCodes();
        if (zipCodes == null || zipCodes.isEmpty()) return List.of();

        UUID runId = query.ingestionRunId();
        List<CanonicalLocation> out = new ArrayList<>();

        for (String zip : zipCodes) {
            KrogerLocationResponse response = apiClient.getLocations(zip, runId);
            List<Location> locations = response != null ? response.getData() : null;
            if (locations == null || locations.isEmpty()) continue;

            for (Location loc : locations) {
                if (loc == null) continue;

                String addressLine1 = loc.getAddress() != null ? loc.getAddress().getAddressLine1() : null;
                String city = loc.getAddress() != null ? loc.getAddress().getCity() : null;
                String state = loc.getAddress() != null ? loc.getAddress().getState() : null;
                String postal = loc.getAddress() != null ? loc.getAddress().getZipCode() : null;

                out.add(new CanonicalLocation(
                        loc.getLocationId(),
                        sourceType().code(),
                        loc.getName(),
                        null, // phone not currently in Kroger DTO
                        addressLine1,
                        city,
                        state,
                        postal,
                        COUNTRY_CODE
                ));
            }
        }

        return out;
    }

    @Override
    public List<CanonicalProductPrice> fetchProducts(ProductQuery query) {
        if (query == null) return List.of();

        List<String> locationIds = query.locationIds();
        List<String> searchTerms = query.searchTerms();

        if (locationIds == null || locationIds.isEmpty() || searchTerms == null || searchTerms.isEmpty()) {
            return List.of();
        }

        UUID runId = query.ingestionRunId();
        Instant observedAt = Instant.now();
        List<CanonicalProductPrice> out = new ArrayList<>();

        for (String locationId : locationIds) {
            for (String term : searchTerms) {
                KrogerProductsResponse response = apiClient.getProducts(locationId, term, runId);
                List<Product> products = response != null ? response.getData() : null;
                if (products == null || products.isEmpty()) continue;

                for (Product product : products) {
                    if (product == null) continue;

                    CanonicalProduct canonicalProduct = new CanonicalProduct(
                            product.getProductId(),
                            product.getUpc(),
                            product.getDescription(),
                            product.getBrand(),
                            toCategoriesArray(product.getCategories()),
                            null, // product page URI not present in current Kroger DTO
                            null, // raw category json not captured
                            null  // raw flags json not captured
                    );

                    Price price = extractFirstPrice(product);
                    if (price == null) continue;

                    BigDecimal regular = price.getRegular() != null ? BigDecimal.valueOf(price.getRegular()) : null;
                    BigDecimal promo = price.getPromo() != null ? BigDecimal.valueOf(price.getPromo()) : null;
                    BigDecimal effective = (promo != null) ? promo : regular;
                    if (effective == null) continue;

                    Boolean onSale = (promo != null && regular != null && promo.compareTo(regular) < 0);

                    CanonicalPriceObservation obs = new CanonicalPriceObservation(
                            observedAt,
                            CURRENCY_CODE,
                            effective,
                            regular,
                            promo,
                            onSale
                    );

                    out.add(new CanonicalProductPrice(locationId, canonicalProduct, obs));
                }
            }
        }

        return out;
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