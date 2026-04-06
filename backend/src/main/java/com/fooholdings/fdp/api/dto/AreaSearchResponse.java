package com.fooholdings.fdp.api.dto;

import java.util.List;

public record AreaSearchResponse(
        String query,
        String level,
        List<AreaSearchResult> results
) {}
