package com.fooholdings.fdp.sources.model;

/**
 * Canonical representation of a source-specific product.
 */
public record CanonicalProduct(
        String sourceProductId,
        String upc,
        String name,
        String brand,
        String[] categories,
        String productPageUri,
        String rawCategoryJson,
        String rawFlagsJson
) { }