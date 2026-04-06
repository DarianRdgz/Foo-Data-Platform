package com.fooholdings.fdp.geo.seed;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fooholdings.fdp.geo.repo.GeoAreaJdbcRepository;

/**
 * Seeds geo_areas from SimpleMaps CSVs (as-downloaded, no renaming).
 *
 * Expected files in seedDir:
 *  - usmetros.csv
 *  - uscounties.csv
 *  - uszips.csv
 */
@Service
public class GeoSeedService {

    private static final Logger log = LoggerFactory.getLogger(GeoSeedService.class);

    private static final Map<String, String> STATE_ABBR_TO_FIPS2 = buildStateAbbrToFips2();

    private final GeoAreaJdbcRepository geoRepo;

    public GeoSeedService(GeoAreaJdbcRepository geoRepo) {
        this.geoRepo = geoRepo;
    }

    @Transactional
    public void seedMetrosFromSeedDir(String seedDir) throws IOException {
        Path file = Path.of(seedDir, "usmetros.csv");
        if (!Files.exists(file)) {
            log.warn("usmetros.csv not found at {} — skipping metro seeding", file);
            return;
        }

        int insertedOrUpdated = 0;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                return;
            }

            Map<String, Integer> idx = headerIndex(header);
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = splitCsvLine(line);

                String metroFips = get(parts, idx, "metro_fips");
                String metroName = firstNonBlank(
                        safeGet(parts, idx, "metro_full"),
                        safeGet(parts, idx, "metro")
                );
                String stateAbbr = get(parts, idx, "state_id");
                String stateFips2 = mapStateAbbrToFips2(stateAbbr);
                BigDecimal centroidLatitude = parseNullableDecimal(parts, idx, "lat");
                BigDecimal centroidLongitude = parseNullableDecimal(parts, idx, "lng");


                UUID stateGeoId = geoRepo.findStateGeoIdByFips(stateFips2)
                        .orElseThrow(() -> new IllegalStateException("Missing state geo_id for fips=" + stateFips2));
                geoRepo.upsertGeoArea(
                        "metro",
                        null,
                        metroFips,
                        null,
                        metroName,
                        stateGeoId,
                        metroName,
                        centroidLatitude,
                        centroidLongitude
                );
                insertedOrUpdated++;
            }
        }

        log.info("Seeded metros from usmetros.csv: {}", insertedOrUpdated);
    }

    @Transactional
    public void seedCountiesFromSeedDir(String seedDir) throws IOException {
        Path file = Path.of(seedDir, "uscounties.csv");
        if (!Files.exists(file)) {
            log.warn("uscounties.csv not found at {} — skipping county seeding", file);
            return;
        }

        int insertedOrUpdated = 0;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                return;
            }

            Map<String, Integer> idx = headerIndex(header);
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = splitCsvLine(line);

                String countyFips5 = get(parts, idx, "county_fips");
                String countyName = firstNonBlank(
                        safeGet(parts, idx, "county"),
                        safeGet(parts, idx, "county_full")
                );

                String stateFips2 = countyFips5.substring(0, 2);

                UUID stateGeoId = geoRepo.findStateGeoIdByFips(stateFips2)
                        .orElseThrow(() -> new IllegalStateException("Missing state geo_id for fips=" + stateFips2));

                String countyFull = safeGet(parts, idx, "county_full");
                String display = (countyFull != null && !countyFull.isBlank())
                        ? countyFull
                        : countyName + " County";

                BigDecimal centroidLatitude = parseNullableDecimal(parts, idx, "lat");
                BigDecimal centroidLongitude = parseNullableDecimal(parts, idx, "lng");

                geoRepo.upsertGeoArea(
                        "county",
                        countyFips5,
                        null,
                        null,
                        countyName,
                        stateGeoId,
                        display,
                        centroidLatitude,
                        centroidLongitude
                );
                insertedOrUpdated++;
            }
        }

        log.info("Seeded counties from uscounties.csv: {}", insertedOrUpdated);
    }

    @Transactional
    public void seedZipsFromSeedDir(String seedDir) throws IOException {
        Path file = Path.of(seedDir, "uszips.csv");
        if (!Files.exists(file)) {
            log.warn("uszips.csv not found at {} — skipping zip seeding", file);
            return;
        }

        int insertedOrUpdated = 0;
        int missingCounties = 0;
        int multiCountyZips = 0;

        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null) {
                return;
            }

            Map<String, Integer> idx = headerIndex(header);
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = splitCsvLine(line);

                String zip = get(parts, idx, "zip");
                String countyFips5 = get(parts, idx, "county_fips");
                String countyWeights = safeGet(parts, idx, "county_weights");

                if (countyWeights != null && countyWeights.contains(":") && countyWeights.contains(",")) {
                    multiCountyZips++;
                }

                UUID countyGeoId = geoRepo.findCountyGeoIdByFips(countyFips5).orElse(null);
                if (countyGeoId == null) {
                    missingCounties++;
                    continue;
                }

                BigDecimal centroidLatitude = parseNullableDecimal(parts, idx, "lat");
                BigDecimal centroidLongitude = parseNullableDecimal(parts, idx, "lng");

                geoRepo.upsertGeoArea(
                        "zip",
                        null,
                        null,
                        zip,
                        zip,
                        countyGeoId,
                        zip,
                        centroidLatitude,
                        centroidLongitude
                );
                insertedOrUpdated++;
            }
        }

        if (missingCounties > 0) {
            log.warn("ZIP seeding skipped {} rows because county geo_areas were missing", missingCounties);
        }
        if (multiCountyZips > 0) {
            log.warn("Detected {} ZIPs with multi-county weights; seeded using primary county_fips.", multiCountyZips);
        }

        log.info("Seeded zips from uszips.csv: {}", insertedOrUpdated);
    }

    private static String mapStateAbbrToFips2(String stateAbbr) {
        String normalized = stateAbbr == null ? null : stateAbbr.trim().toUpperCase();
        String fips = normalized == null ? null : STATE_ABBR_TO_FIPS2.get(normalized);
        if (fips == null) {
            throw new IllegalArgumentException("Unknown state abbreviation: " + stateAbbr);
        }
        return fips;
    }

    private static Map<String, Integer> headerIndex(String headerLine) {
        String[] cols = splitCsvLine(headerLine);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            idx.put(cols[i].trim().toLowerCase(), i);
        }
        return idx;
    }

    private static String get(String[] parts, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= parts.length) {
            throw new IllegalArgumentException("Missing column: " + col);
        }
        String value = parts[i].trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank value for column: " + col);
        }
        return value;
    }

    private static String safeGet(String[] parts, Map<String, Integer> idx, String col) {
        Integer i = idx.get(col);
        if (i == null || i >= parts.length) {
            return null;
        }
        String value = parts[i];
        return value == null ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static BigDecimal parseNullableDecimal(String[] parts, Map<String, Integer> idx, String col) {
        String raw = safeGet(parts, idx, col);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim());
    }

    /**
     * Minimal CSV splitter that handles basic quoted fields.
     */
    private static String[] splitCsvLine(String line) {
        if (!line.contains("\"")) {
            return line.split(",", -1);
        }

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        out.add(cur.toString());
        return out.toArray(String[]::new);
    }

    private static Map<String, String> buildStateAbbrToFips2() {
        Map<String, String> m = new HashMap<>();
        m.put("AL", "01"); m.put("AK", "02"); m.put("AZ", "04"); m.put("AR", "05"); m.put("CA", "06");
        m.put("CO", "08"); m.put("CT", "09"); m.put("DE", "10"); m.put("DC", "11"); m.put("FL", "12");
        m.put("GA", "13"); m.put("HI", "15"); m.put("ID", "16"); m.put("IL", "17"); m.put("IN", "18");
        m.put("IA", "19"); m.put("KS", "20"); m.put("KY", "21"); m.put("LA", "22"); m.put("ME", "23");
        m.put("MD", "24"); m.put("MA", "25"); m.put("MI", "26"); m.put("MN", "27"); m.put("MS", "28");
        m.put("MO", "29"); m.put("MT", "30"); m.put("NE", "31"); m.put("NV", "32"); m.put("NH", "33");
        m.put("NJ", "34"); m.put("NM", "35"); m.put("NY", "36"); m.put("NC", "37"); m.put("ND", "38");
        m.put("OH", "39"); m.put("OK", "40"); m.put("OR", "41"); m.put("PA", "42"); m.put("RI", "44");
        m.put("SC", "45"); m.put("SD", "46"); m.put("TN", "47"); m.put("TX", "48"); m.put("UT", "49");
        m.put("VT", "50"); m.put("VA", "51"); m.put("WA", "53"); m.put("WV", "54"); m.put("WI", "55");
        m.put("WY", "56");
        return m;
    }
}