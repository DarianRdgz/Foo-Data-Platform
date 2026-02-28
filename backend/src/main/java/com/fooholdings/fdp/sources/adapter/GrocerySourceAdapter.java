package com.fooholdings.fdp.sources.adapter;

import java.util.List;

import com.fooholdings.fdp.sources.model.CanonicalLocation;
import com.fooholdings.fdp.sources.model.CanonicalProductPrice;
import com.fooholdings.fdp.sources.model.LocationQuery;
import com.fooholdings.fdp.sources.model.ProductQuery;
import com.fooholdings.fdp.sources.model.SourceType;

/**
 * 
 * Core calls this interface.
 * Implementations live under com.fooholdings.fdp.sources.<name>.
 *
 * IMPORTANT:
 * - Core MUST NOT import any source-specific DTOs.
 * - Adapters return ONLY canonical DTOs.
 */
public interface GrocerySourceAdapter {

    SourceType sourceType();

    List<CanonicalLocation> fetchLocations(LocationQuery query);

    List<CanonicalProductPrice> fetchProducts(ProductQuery query);
}