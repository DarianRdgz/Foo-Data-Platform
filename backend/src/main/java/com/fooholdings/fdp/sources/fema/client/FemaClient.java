package com.fooholdings.fdp.sources.fema.client;

import java.io.BufferedReader;
import java.io.StringReader;
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
        String[] lines = csv.split("\n", -1);

        if (lines.length < 2) return records;

        String[] header = splitCsvLine(lines[0]);
        int stateIdx    = indexOf(header, "fipsStateCode",   "state");
        int countyIdx   = indexOf(header, "fipsCountyCode",  "countyCode");
        int typeIdx     = indexOf(header, "incidentType");
        int dateIdx     = indexOf(header, "declarationDate");

        if (stateIdx < 0 || typeIdx < 0 || dateIdx < 0) {
            throw new IllegalStateException(
                "FEMA CSV missing required columns. Headers: " + lines[0]);
        }

        int skipped = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] cols = splitCsvLine(line);

            String stateFips    = valueAt(cols, stateIdx);
            String countyFips3  = valueAt(cols, countyIdx);   // may be null or "000"
            String incidentType = valueAt(cols, typeIdx);
            String declDate     = valueAt(cols, dateIdx);

            if (stateFips == null || incidentType == null || declDate == null) {
                skipped++;
                continue;
            }

            // Pad state fips to 2 digits if needed
            if (stateFips.length() == 1) stateFips = "0" + stateFips;

            // "000" means statewide declaration — no county
            boolean statewide = countyFips3 == null || countyFips3.equals("000");
            String countyFips5 = statewide ? null : stateFips + zeroPad(countyFips3, 3);

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
}