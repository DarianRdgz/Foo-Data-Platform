package com.fooholdings.fdp.geo.support;

import java.util.Map;

/**
 * Converts U.S. state/territory two-letter abbreviations to their Census FIPS codes.
 *
 * Used by any source that receives state abbreviations rather than FIPS codes
 * (e.g. College Scorecard API returns "TX", GeoAreaJdbcRepository expects "48").
 *
 * Package-level note: this class intentionally duplicates the ABBR→FIPS table
 * from sources.zillow.ingestion.StateFipsLookup, which is package-private and
 * has a different method signature. Both can be consolidated in a future cleanup.
 */
public final class StateAbbreviationLookup {

    private static final Map<String, String> ABBR_TO_FIPS = Map.ofEntries(
            Map.entry("AL", "01"), Map.entry("AK", "02"), Map.entry("AZ", "04"),
            Map.entry("AR", "05"), Map.entry("CA", "06"), Map.entry("CO", "08"),
            Map.entry("CT", "09"), Map.entry("DE", "10"), Map.entry("DC", "11"),
            Map.entry("FL", "12"), Map.entry("GA", "13"), Map.entry("HI", "15"),
            Map.entry("ID", "16"), Map.entry("IL", "17"), Map.entry("IN", "18"),
            Map.entry("IA", "19"), Map.entry("KS", "20"), Map.entry("KY", "21"),
            Map.entry("LA", "22"), Map.entry("ME", "23"), Map.entry("MD", "24"),
            Map.entry("MA", "25"), Map.entry("MI", "26"), Map.entry("MN", "27"),
            Map.entry("MS", "28"), Map.entry("MO", "29"), Map.entry("MT", "30"),
            Map.entry("NE", "31"), Map.entry("NV", "32"), Map.entry("NH", "33"),
            Map.entry("NJ", "34"), Map.entry("NM", "35"), Map.entry("NY", "36"),
            Map.entry("NC", "37"), Map.entry("ND", "38"), Map.entry("OH", "39"),
            Map.entry("OK", "40"), Map.entry("OR", "41"), Map.entry("PA", "42"),
            Map.entry("RI", "44"), Map.entry("SC", "45"), Map.entry("SD", "46"),
            Map.entry("TN", "47"), Map.entry("TX", "48"), Map.entry("UT", "49"),
            Map.entry("VT", "50"), Map.entry("VA", "51"), Map.entry("WA", "53"),
            Map.entry("WV", "54"), Map.entry("WI", "55"), Map.entry("WY", "56")
    );

    private StateAbbreviationLookup() {}

    /**
     * Returns the 2-digit FIPS code for the given state abbreviation.
     *
     * @param abbreviation case-insensitive two-letter state abbreviation (e.g. "TX")
     * @return FIPS code string (e.g. "48"), or null if the abbreviation is unrecognised
     */
    public static String fipsFromAbbreviation(String abbreviation) {
        if (abbreviation == null || abbreviation.isBlank()) return null;
        return ABBR_TO_FIPS.get(abbreviation.trim().toUpperCase());
    }
}