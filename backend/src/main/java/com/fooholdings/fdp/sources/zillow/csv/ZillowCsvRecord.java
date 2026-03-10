package com.fooholdings.fdp.sources.zillow.csv;

import java.time.LocalDate;
import java.util.Map;

public record ZillowCsvRecord(
        String regionId,
        String regionName,
        String regionType,
        String stateName,
        String stateCode,
        String metroName,
        String countyName,
        Map<LocalDate, Double> values
) {
}