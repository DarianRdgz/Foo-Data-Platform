package com.fooholdings.fdp.sources.cde.client;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.sources.cde.config.CdeProperties;

@Component
public class CdeClient {

    private static final Logger log = LoggerFactory.getLogger(CdeClient.class);

    private final RestClient restClient;
    private final CdeProperties props;

    public CdeClient(RestClient.Builder builder, CdeProperties props) {
        this.props = props;
        this.restClient = builder
                .baseUrl(props.baseUrl())
                .build();
    }

    public List<CdeCrimeRow> fetchOffensesKnown() {
        return fetchCsv(props.offensesKnownPath());
    }

    public List<CdeCrimeRow> fetchClearances() {
        return fetchCsv(props.clearancesPath());
    }

    private List<CdeCrimeRow> fetchCsv(String path) {
        log.info("CDE: fetching path={} apiKeyPresent={} apiKeyLength={}",
                path,
                StringUtils.hasText(props.apiKey()),
                props.apiKey() == null ? "null" : props.apiKey().length());

        String csv = restClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path(path)
                    .queryParam("csv", "true")
                    .build())
            .header("X-Api-Key", props.apiKey())
            .accept(MediaType.TEXT_PLAIN, MediaType.valueOf("text/csv"), MediaType.ALL)
            .retrieve()
            .body(String.class);

        if (!StringUtils.hasText(csv)) {
            log.warn("CDE: empty response from {}", path);
            return List.of();
        }

        List<String> lines = csv.lines().toList();
        if (lines.isEmpty()) {
            return List.of();
        }

        // Strict header detection ──────────────────────────────────────
        // We will not silently produce null-valued rows if the FBI changes
        // column names. Fail loud so the problem is caught immediately.
        String[] header = splitCsv(lines.getFirst());
        int yearIdx         = -1;
        int stateIdx        = -1;
        int violentRateIdx  = -1;
        int propertyRateIdx = -1;
        int totalIdx        = -1;
        int rateIdx         = -1;

        for (int i = 0; i < header.length; i++) {
            String col = normalize(header[i]);
            if (col.equals("year"))                                              yearIdx = i;
            if (col.equals("state") || col.equals("state_name"))                stateIdx = i;
            if (col.equals("violent_crime_rate") || col.equals("violent_rate")) violentRateIdx = i;
            if (col.equals("property_crime_rate") || col.equals("property_rate")) propertyRateIdx = i;
            if (col.equals("actual") || col.equals("offense_count") || col.equals("incidents")) totalIdx = i;
            if (col.equals("rate") || col.equals("offenses_per_100000") || col.equals("per_100000")) rateIdx = i;
        }

        // year and state are required; rate columns are expected but warn rather than hard-fail
        if (yearIdx  < 0) throw new IllegalStateException("CDE CSV from " + path + " has no 'year' column. Headers: " + lines.getFirst());
        if (stateIdx < 0) throw new IllegalStateException("CDE CSV from " + path + " has no 'state' column. Headers: " + lines.getFirst());
        if (violentRateIdx < 0 || propertyRateIdx < 0) {
            log.warn("CDE CSV from {} is missing one or more rate columns — " +
                     "violent_rate_idx={}, property_rate_idx={}. Some categories will be skipped.",
                     path, violentRateIdx, propertyRateIdx);
        }

        List<CdeCrimeRow> rows = new ArrayList<>();
        int minCols = Math.max(Math.max(yearIdx, stateIdx),
                     Math.max(Math.max(violentRateIdx, propertyRateIdx),
                              Math.max(totalIdx, rateIdx)));

        for (String line : lines.subList(1, lines.size())) {
            String[] cols = splitCsv(line);
            if (cols.length <= minCols) {
                continue;
            }
            rows.add(new CdeCrimeRow(
                    valueAt(cols, yearIdx),
                    valueAt(cols, stateIdx),
                    parseDouble(valueAt(cols, violentRateIdx)),
                    parseDouble(valueAt(cols, propertyRateIdx)),
                    parseDouble(valueAt(cols, totalIdx)),
                    parseDouble(valueAt(cols, rateIdx))
            ));
        }
        log.info("CDE: parsed {} rows from {}", rows.size(), path);
        return rows;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String[] splitCsv(String line) {
        // Handles quoted fields that may contain commas
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
    }

    private static String valueAt(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return null;
        if (cols[idx] == null) return null;
        String value = cols[idx].trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value.isBlank() ? null : value;
    }

    private static Double parseDouble(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return NumberFormat.getNumberInstance(Locale.US).parse(raw).doubleValue();
        } catch (ParseException ex) {
            return null;
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "_");
    }

    public record CdeCrimeRow(
            String year,
            String state,
            Double violentRate,
            Double propertyRate,
            Double totalIncidents,
            Double totalIncidentsPer100k
    ) {}
}
