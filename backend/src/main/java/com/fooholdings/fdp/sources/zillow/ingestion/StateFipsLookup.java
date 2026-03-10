package com.fooholdings.fdp.sources.zillow.ingestion;

import java.util.Map;

final class StateFipsLookup {

    private static final Map<String, String> ABBR_TO_FIPS = Map.ofEntries(
            Map.entry("AL", "01"), Map.entry("AK", "02"), Map.entry("AZ", "04"), Map.entry("AR", "05"),
            Map.entry("CA", "06"), Map.entry("CO", "08"), Map.entry("CT", "09"), Map.entry("DE", "10"),
            Map.entry("DC", "11"), Map.entry("FL", "12"), Map.entry("GA", "13"), Map.entry("HI", "15"),
            Map.entry("ID", "16"), Map.entry("IL", "17"), Map.entry("IN", "18"), Map.entry("IA", "19"),
            Map.entry("KS", "20"), Map.entry("KY", "21"), Map.entry("LA", "22"), Map.entry("ME", "23"),
            Map.entry("MD", "24"), Map.entry("MA", "25"), Map.entry("MI", "26"), Map.entry("MN", "27"),
            Map.entry("MS", "28"), Map.entry("MO", "29"), Map.entry("MT", "30"), Map.entry("NE", "31"),
            Map.entry("NV", "32"), Map.entry("NH", "33"), Map.entry("NJ", "34"), Map.entry("NM", "35"),
            Map.entry("NY", "36"), Map.entry("NC", "37"), Map.entry("ND", "38"), Map.entry("OH", "39"),
            Map.entry("OK", "40"), Map.entry("OR", "41"), Map.entry("PA", "42"), Map.entry("RI", "44"),
            Map.entry("SC", "45"), Map.entry("SD", "46"), Map.entry("TN", "47"), Map.entry("TX", "48"),
            Map.entry("UT", "49"), Map.entry("VT", "50"), Map.entry("VA", "51"), Map.entry("WA", "53"),
            Map.entry("WV", "54"), Map.entry("WI", "55"), Map.entry("WY", "56")
    );

    private StateFipsLookup() {
    }

    static String fromCode(String preferred, String code, String fullName) {
        if (preferred != null) {
            String direct = ABBR_TO_FIPS.get(preferred.trim().toUpperCase());
            if (direct != null) {
                return direct;
            }
        }
        if (code != null) {
            String direct = ABBR_TO_FIPS.get(code.trim().toUpperCase());
            if (direct != null) {
                return direct;
            }
        }
        if (fullName != null) {
            return switch (fullName.trim().toLowerCase()) {
                case "alabama" -> "01";
                case "alaska" -> "02";
                case "arizona" -> "04";
                case "arkansas" -> "05";
                case "california" -> "06";
                case "colorado" -> "08";
                case "connecticut" -> "09";
                case "delaware" -> "10";
                case "district of columbia" -> "11";
                case "florida" -> "12";
                case "georgia" -> "13";
                case "hawaii" -> "15";
                case "idaho" -> "16";
                case "illinois" -> "17";
                case "indiana" -> "18";
                case "iowa" -> "19";
                case "kansas" -> "20";
                case "kentucky" -> "21";
                case "louisiana" -> "22";
                case "maine" -> "23";
                case "maryland" -> "24";
                case "massachusetts" -> "25";
                case "michigan" -> "26";
                case "minnesota" -> "27";
                case "mississippi" -> "28";
                case "missouri" -> "29";
                case "montana" -> "30";
                case "nebraska" -> "31";
                case "nevada" -> "32";
                case "new hampshire" -> "33";
                case "new jersey" -> "34";
                case "new mexico" -> "35";
                case "new york" -> "36";
                case "north carolina" -> "37";
                case "north dakota" -> "38";
                case "ohio" -> "39";
                case "oklahoma" -> "40";
                case "oregon" -> "41";
                case "pennsylvania" -> "42";
                case "rhode island" -> "44";
                case "south carolina" -> "45";
                case "south dakota" -> "46";
                case "tennessee" -> "47";
                case "texas" -> "48";
                case "utah" -> "49";
                case "vermont" -> "50";
                case "virginia" -> "51";
                case "washington" -> "53";
                case "west virginia" -> "54";
                case "wisconsin" -> "55";
                case "wyoming" -> "56";
                default -> null;
            };
        }
        return null;
    }
}