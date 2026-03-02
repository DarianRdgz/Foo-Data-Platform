package com.fooholdings.fdp.api.dto;

import java.time.Instant;
import java.util.UUID;

//Stable API DTO for product reads.
public record ProductResponse(
        UUID id,
        String source,
        String sourceProductId,
        String upc,
        String name,
        String brand,
        String productPageUri,
        Instant firstSeenAt,
        Instant lastSeenAt
) {}