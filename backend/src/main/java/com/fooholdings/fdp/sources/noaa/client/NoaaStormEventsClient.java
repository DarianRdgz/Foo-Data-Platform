package com.fooholdings.fdp.sources.noaa.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fooholdings.fdp.sources.noaa.config.NoaaProperties;
import com.fooholdings.fdp.sources.noaa.ingestion.NoaaStormEventRecord;

/**
 * Fetches NOAA Storm Events bulk CSV data.
 *
 * NOAA publishes one gzipped CSV per year at:
 *   {baseUrl}/StormEvents_details-ftp_v1.0_d{YEAR}_c{YYYYMMDD}.csv.gz
 *
 * The creation date suffix in the filename changes whenever NOAA updates the file,
 * so we must discover the current filename by listing the directory index page
 * and matching a pattern rather than constructing the URL directly.
 *
 * Only "C" (county) type zones are aggregated; "Z" (forecast zone) rows are dropped
 * because zone-to-county mapping requires an additional crosswalk file.
 */
@Component
public class NoaaStormEventsClient {

    private static final Logger log = LoggerFactory.getLogger(NoaaStormEventsClient.class);

    // Matches filenames like: StormEvents_details-ftp_v1.0_d2024_c20250115.csv.gz
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("StormEvents_details-ftp_v1\\.0_d(\\d{4})_c\\d{8}\\.csv\\.gz");

    private final RestClient restClient;
    private final NoaaProperties props;

    public NoaaStormEventsClient(RestClient.Builder builder, NoaaProperties props) {
        this.props = props;
        this.restClient = builder.build();
    }

    /**
     * Fetches storm events for the given year.
     *
     * @param year calendar year (e.g. 2024)
     * @return parsed storm event records; empty list if the file is not found
     */
    public List<NoaaStormEventRecord> fetchEventsForYear(int year) {
        String filename = discoverFilename(year);
        if (filename == null) {
            log.warn("NOAA: no storm events file found for year={}", year);
            return List.of();
        }

        String url = props.stormEventsBaseUrl() + "/" + filename;
        log.info("NOAA: downloading {}", url);

        return restClient.get()
                .uri(url)
                .exchange((req, resp) -> {
                    try (InputStream raw = resp.getBody();
                         GZIPInputStream gz = new GZIPInputStream(raw);
                         BufferedReader reader = new BufferedReader(
                                 new InputStreamReader(gz, StandardCharsets.UTF_8))) {
                        return parseCsv(reader, year);
                    } catch (IOException e) {
                        throw new RuntimeException("NOAA: failed to decompress/parse " + url, e);
                    }
                });
    }

    /**
     * Fetches events for the current and immediately preceding year to handle
     * delayed publication (NOAA sometimes publishes current year data weeks late).
     */
    public List<NoaaStormEventRecord> fetchRecentEvents() {
        int currentYear = java.time.Year.now().getValue();
        List<NoaaStormEventRecord> all = new ArrayList<>();
        all.addAll(fetchEventsForYear(currentYear));
        all.addAll(fetchEventsForYear(currentYear - 1));
        log.info("NOAA: total {} records across {} and {}", all.size(), currentYear, currentYear - 1);
        return all;
    }

    // ── Directory listing ─────────────────────────────────────────────────

    private String discoverFilename(int year) {
        String indexHtml = restClient.get()
                .uri(props.stormEventsBaseUrl() + "/")
                .retrieve()
                .body(String.class);

        if (indexHtml == null) return null;

        Matcher m = FILENAME_PATTERN.matcher(indexHtml);
        String candidate = null;
        while (m.find()) {
            if (Integer.parseInt(m.group(1)) == year) {
                candidate = m.group(0); // last match wins (most recent creation date)
            }
        }
        return candidate;
    }

    // ── CSV parsing ───────────────────────────────────────────────────────

    private List<NoaaStormEventRecord> parseCsv(BufferedReader reader, int year) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) return List.of();

        String[] header = headerLine.split(",", -1);
        int stateFipsIdx    = indexOf(header, "STATE_FIPS");
        int czFipsIdx       = indexOf(header, "CZ_FIPS");
        int czTypeIdx       = indexOf(header, "CZ_TYPE");
        int czNameIdx       = indexOf(header, "CZ_NAME");
        int damagePropertyIdx = indexOf(header, "DAMAGE_PROPERTY");
        int damageCropsIdx  = indexOf(header, "DAMAGE_CROPS");
        int deathsDirectIdx = indexOf(header, "DEATHS_DIRECT");
        int deathsIndirectIdx = indexOf(header, "DEATHS_INDIRECT");

        if (stateFipsIdx < 0 || czFipsIdx < 0) {
            throw new IllegalStateException("NOAA CSV missing STATE_FIPS or CZ_FIPS columns");
        }

        List<NoaaStormEventRecord> records = new ArrayList<>();
        String line;
        int skipped = 0;

        while ((line = reader.readLine()) != null) {
            String[] cols = line.split(",", -1);

            String czType = unquote(valueAt(cols, czTypeIdx));
            if (!"C".equalsIgnoreCase(czType)) continue; // county rows only

            String stateFips2 = zeroPad(unquote(valueAt(cols, stateFipsIdx)), 2);
            String czFips3    = zeroPad(unquote(valueAt(cols, czFipsIdx)), 3);
            String czName     = unquote(valueAt(cols, czNameIdx));

            if (stateFips2 == null || czFips3 == null) { skipped++; continue; }

            String countyFips5 = stateFips2 + czFips3;

            double damageUsd = parseDamage(valueAt(cols, damagePropertyIdx))
                             + parseDamage(valueAt(cols, damageCropsIdx));

            int deaths = parseInt(valueAt(cols, deathsDirectIdx))
                       + parseInt(valueAt(cols, deathsIndirectIdx));

            records.add(new NoaaStormEventRecord(
                    stateFips2, countyFips5, czName, damageUsd, deaths, year));
        }

        if (skipped > 0) log.warn("NOAA: skipped {} rows with missing fips in year={}", skipped, year);
        log.info("NOAA: parsed {} county-level event records for year={}", records.size(), year);
        return records;
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(unquote(header[i].trim()))) return i;
        }
        return -1;
    }

    private static String valueAt(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return null;
        return cols[idx];
    }

    private static String unquote(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s.isEmpty() ? null : s;
    }

    private static String zeroPad(String s, int length) {
        if (s == null) return null;
        return "0".repeat(Math.max(0, length - s.length())) + s;
    }

    /**
     * Parses NOAA abbreviated damage values: "1.5K" → 1500.0, "2.5M" → 2500000.0.
     * Returns 0.0 for null, blank, or "0".
     */
    static double parseDamage(String raw) {
        if (raw == null || raw.isBlank() || raw.equals("0")) return 0.0;
        raw = unquote(raw.trim().toUpperCase());
        if (raw == null) return 0.0;
        try {
            if (raw.endsWith("K")) return Double.parseDouble(raw.substring(0, raw.length() - 1)) * 1_000;
            if (raw.endsWith("M")) return Double.parseDouble(raw.substring(0, raw.length() - 1)) * 1_000_000;
            if (raw.endsWith("B")) return Double.parseDouble(raw.substring(0, raw.length() - 1)) * 1_000_000_000;
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        try { return Integer.parseInt(unquote(raw.trim())); }
        catch (NumberFormatException e) { return 0; }
    }
}