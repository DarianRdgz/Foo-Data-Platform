package com.fooholdings.fdp.sources.fema.client;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.sources.fema.config.FemaProperties;
import com.fooholdings.fdp.sources.fema.ingestion.FemaDisasterDeclarationRecord;

/**
 * Downloads the OpenFEMA DisasterDeclarationsSummaries CSV and parses it into records.
 *
 * The CSV is typically ~60MB. We read it as a String and process line by line.
 * Required columns: fipsStateCode, fipsCountyCode, incidentType, declarationDate.
 * Missing or malformed rows are skipped with a warn log.
 */
@Component
public class FemaClient {

    private static final Logger log = LoggerFactory.getLogger(FemaClient.class);

    private final RestClient restClient;
    private final FemaProperties props;

    public FemaClient(RestClient.Builder builder, FemaProperties props) {
        this.props = props;
        this.restClient = builder.build();
    }

    public List<FemaDisasterDeclarationRecord> fetchDeclarations() {
        log.info("FEMA: downloading DisasterDeclarationsSummaries from {}", props.disasterDeclarationsUrl());

        String csv = restClient.get()
                .uri(props.disasterDeclarationsUrl())
                .retrieve()
                .body(String.class);

        if (csv == null || csv.isBlank()) {
            log.warn("FEMA: empty response from {}", props.disasterDeclarationsUrl());
            return List.of();
        }

        return parseDeclarations(csv);
    }

    private List<FemaDisasterDeclarationRecord> parseDeclarations(String csv) {
        List<FemaDisasterDeclarationRecord> records = new ArrayList<>();
        
        if (csv.startsWith("\uFEFF")) {
            log.warn("FEMA: stripping UTF-8 BOM — this would have caused 0 rows silently");
            csv = csv.substring(1);
        }
        
        String[] lines = csv.split("\n", -1);

        if (lines.length < 2) return records;

        String[] header = splitCsvLine(lines[0]);
        int stateIdx    = indexOf(header, "fipsStateCode",   "state");
        int countyIdx   = indexOf(header, "fipsCountyCode",  "countyCode");
        int typeIdx     = indexOf(header, "incidentType");
        int dateIdx     = indexOf(header, "declarationDate");

        log.info("FEMA: column indexes — fipsStateCode={}, fipsCountyCode={}, incidentType={}, declarationDate={}",
        stateIdx, countyIdx, typeIdx, dateIdx);

        if (stateIdx < 0 || typeIdx < 0 || dateIdx < 0) {
            throw new IllegalStateException(
                "FEMA CSV missing required columns. Headers: " + lines[0]);
        }

        int skipped = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] cols = splitCsvLine(line);

            String stateFips    = normalizeStateFips(valueAt(cols, stateIdx));
            String countyFips3  = normalizeCountyFips(valueAt(cols, countyIdx));   // may be null or "000"
            String incidentType = valueAt(cols, typeIdx);
            String declDate     = valueAt(cols, dateIdx);

            if (stateFips == null) {
                skipped++;
                continue;
            }

            // Pad state fips to 2 digits if needed
            if (stateFips.length() == 1) stateFips = "0" + stateFips;

            // "000" means statewide declaration — no county
            boolean statewide = countyFips3 == null || countyFips3.equals("000");
            String countyFips5 = statewide ? null : stateFips + countyFips3;

            records.add(new FemaDisasterDeclarationRecord(
                    stateFips, countyFips5, incidentType, declDate));
        }

        if (skipped > 0) {
            log.warn("FEMA: skipped {} malformed rows during parse", skipped);
        }
        log.info("FEMA: parsed {} declaration records", records.size());
        return records;
    }

    private static int indexOf(String[] header, String... candidates) {
        for (int i = 0; i < header.length; i++) {
            String col = header[i].trim().replace("\"", "").toLowerCase();
            for (String candidate : candidates) {
                if (col.equalsIgnoreCase(candidate)) return i;
            }
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return null;
        String v = cols[idx].trim().replace("\"", "");
        return v.isEmpty() ? null : v;
    }

    private static String[] splitCsvLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String zeroPad(String s, int length) {
        if (s == null) return null;
        return "0".repeat(Math.max(0, length - s.length())) + s;
    }

    private static String normalizeStateFips(String raw) {
        if (raw == null) return null;

        String v = raw.trim().replace("\"", "");
        if (v.endsWith(".0")) v = v.substring(0, v.length() - 2);
        if (v.isBlank()) return null;

        // Already numeric FIPS
        if (v.matches("\\d{1,2}")) {
            return v.length() == 1 ? "0" + v : v;
        }

        // FEMA is giving postal abbreviations in this feed; convert to numeric FIPS
        return switch (v.toUpperCase()) {
            case "AL" -> "01";
            case "AK" -> "02";
            case "AZ" -> "04";
            case "AR" -> "05";
            case "CA" -> "06";
            case "CO" -> "08";
            case "CT" -> "09";
            case "DE" -> "10";
            case "DC" -> "11";
            case "FL" -> "12";
            case "GA" -> "13";
            case "HI" -> "15";
            case "ID" -> "16";
            case "IL" -> "17";
            case "IN" -> "18";
            case "IA" -> "19";
            case "KS" -> "20";
            case "KY" -> "21";
            case "LA" -> "22";
            case "ME" -> "23";
            case "MD" -> "24";
            case "MA" -> "25";
            case "MI" -> "26";
            case "MN" -> "27";
            case "MS" -> "28";
            case "MO" -> "29";
            case "MT" -> "30";
            case "NE" -> "31";
            case "NV" -> "32";
            case "NH" -> "33";
            case "NJ" -> "34";
            case "NM" -> "35";
            case "NY" -> "36";
            case "NC" -> "37";
            case "ND" -> "38";
            case "OH" -> "39";
            case "OK" -> "40";
            case "OR" -> "41";
            case "PA" -> "42";
            case "RI" -> "44";
            case "SC" -> "45";
            case "SD" -> "46";
            case "TN" -> "47";
            case "TX" -> "48";
            case "UT" -> "49";
            case "VT" -> "50";
            case "VA" -> "51";
            case "WA" -> "53";
            case "WV" -> "54";
            case "WI" -> "55";
            case "WY" -> "56";
            case "AS" -> "60";
            case "GU" -> "66";
            case "MP" -> "69";
            case "PR" -> "72";
            case "VI" -> "78";
            default -> null;
        };
    }

    private static String normalizeCountyFips(String raw) {
        if (raw == null) return null;

        String v = raw.trim().replace("\"", "");
        if (v.endsWith(".0")) v = v.substring(0, v.length() - 2);
        if (v.isBlank()) return null;

        // Keep only numeric county codes
        if (!v.matches("\\d{1,3}")) return null;

        return zeroPad(v, 3);
    }
}