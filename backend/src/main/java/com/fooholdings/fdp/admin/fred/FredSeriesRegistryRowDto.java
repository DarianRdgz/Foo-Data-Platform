package com.fooholdings.fdp.admin.fred;

import java.time.LocalDate;

public record FredSeriesRegistryRowDto(
        String seriesId,
        String category,
        String geoLevel,
        String geoKey,
        boolean enabled,
        LocalDate lastObservationDate
) {}