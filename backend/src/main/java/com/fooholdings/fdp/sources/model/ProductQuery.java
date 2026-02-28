package com.fooholdings.fdp.sources.model;

import java.util.List;
import java.util.UUID;

/**
 * Canonical product query used by all source adapters.
 */
public record ProductQuery(
        List<String> locationIds,
        List<String> searchTerms,
        UUID ingestionRunId
) { }