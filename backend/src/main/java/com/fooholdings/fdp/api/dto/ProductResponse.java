package com.fooholdings.fdp.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

//Stable API DTO for product reads.
public record ProductResponse(
        UUID id,
        String source,
        String sourceProductId,
        String upc,
        String name,
        String brand,
        List<String> categories,
        String productPageUri,
        Instant firstSeenAt,
        Instant lastSeenAt
) {}