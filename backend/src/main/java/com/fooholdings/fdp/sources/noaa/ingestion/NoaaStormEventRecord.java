package com.fooholdings.fdp.sources.noaa.ingestion;

/**
 * Parsed row from NOAA Storm Events CSV (county-type zones only).
 *
 * @param stateFips2    two-digit state FIPS
 * @param countyFips5   five-digit county FIPS (stateFips2 + czFips3)
 * @param countyName    county name from NOAA (used as fallback geo resolver)
 * @param estimatedDamageUsd total property + crop damage in USD
 * @param deaths        direct + indirect deaths
 * @param year          data year
 */
public record NoaaStormEventRecord(
        String stateFips2,
        String countyFips5,
        String countyName,
        double estimatedDamageUsd,
        int deaths,
        int year
) {}