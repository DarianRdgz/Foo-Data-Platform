package com.fooholdings.fdp.sources.model;

import java.util.List;
import java.util.UUID;

/**
 * Canonical location query used by all source adapters.
 */
public record LocationQuery(
        List<String> zipCodes,
        UUID ingestionRunId
) { }