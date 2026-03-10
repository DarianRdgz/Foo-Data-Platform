package com.fooholdings.fdp.sources.fred.ingestion;

/**
 * One entry from the FRED series catalog.
 *
 * geoLevel    - national | state | county | metro
 * geoKeyType  - none | fips | cbsa  (passed to GeoAreaJdbcRepository.findGeoId)
 * geoKey      - the lookup value (e.g. "48" for Texas state FIPS, "US" for national)
 */
public record FredSeriesDefinition(
        String seriesId,
        String category,
        String geoLevel,
        String geoKeyType,
        String geoKey,
        boolean enabled
) {}