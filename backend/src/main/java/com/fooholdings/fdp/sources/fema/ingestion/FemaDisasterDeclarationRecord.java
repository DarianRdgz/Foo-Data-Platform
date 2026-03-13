package com.fooholdings.fdp.sources.fema.ingestion;

/**
 * Single parsed row from the OpenFEMA DisasterDeclarationsSummaries CSV.
 *
 * @param stateFips2   two-digit state FIPS (e.g. "48")
 * @param countyFips5  five-digit county FIPS (e.g. "48113"), or null for statewide declarations
 * @param incidentType incident type string from FEMA (e.g. "Severe Storm(s)", "Hurricane")
 * @param declarationDate ISO-8601 date string (e.g. "2023-04-15T00:00:00.000Z")
 */
public record FemaDisasterDeclarationRecord(
        String stateFips2,
        String countyFips5,
        String incidentType,
        String declarationDate
) {}