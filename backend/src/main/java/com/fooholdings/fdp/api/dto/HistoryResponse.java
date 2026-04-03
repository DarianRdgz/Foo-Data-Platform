package com.fooholdings.fdp.api.dto;

import java.util.List;
import java.util.UUID;

public record HistoryResponse(
        UUID geoId,
        String geoLevel,
        String category,
        int periodsRequested,
        List<HistoryPoint> points
) {}
