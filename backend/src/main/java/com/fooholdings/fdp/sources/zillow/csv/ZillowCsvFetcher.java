package com.fooholdings.fdp.sources.zillow.csv;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;

@Component
public class ZillowCsvFetcher {

    private static final Logger log = LoggerFactory.getLogger(ZillowCsvFetcher.class);

    private final ZillowProperties props;
    private final RestClient restClient;

    public ZillowCsvFetcher(ZillowProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    public List<ZillowCsvRecord> fetch(ZillowMetricFileSpec spec) {
        Path localFile = props.dataDir().resolve(spec.relativePath());
        if (Files.exists(localFile)) {
            return parseLocal(localFile);
        }
        return parseRemote(spec);
    }

    private List<ZillowCsvRecord> parseLocal(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Zillow CSV: " + file, e);
        }
    }

    private List<ZillowCsvRecord> parseRemote(ZillowMetricFileSpec spec) {
        String url = props.baseUrl().endsWith("/")
                ? props.baseUrl() + spec.relativePath()
                : props.baseUrl() + "/" + spec.relativePath();

        RuntimeException last = null;
        for (int attempt = 1; attempt <= Math.max(props.downloadRetries(), 1); attempt++) {
            try {
                byte[] body = restClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .body(byte[].class);

                if (body == null || body.length == 0) {
                    throw new IllegalStateException("Empty Zillow CSV response: " + url);
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8))) {
                    return parse(reader);
                }
            } catch (IOException | RestClientException | IllegalStateException e) {
                last = new IllegalStateException("Failed to download Zillow CSV: " + url + " (attempt " + attempt + ")", e);
                log.warn("Zillow download failed for {} on attempt {}: {}", spec.relativePath(), attempt, e.getMessage());
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("Failed to download Zillow CSV: " + url);
    }

    List<ZillowCsvRecord> parse(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            return List.of();
        }

        List<String> headers = ZillowCsvSupport.splitCsvLine(headerLine);

        Map<Integer, LocalDate> dateColumns = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = ZillowCsvSupport.normalize(headers.get(i));
            if (header.matches("\\d{4}-\\d{2}-\\d{2}")) {
                dateColumns.put(i, LocalDate.parse(header).withDayOfMonth(1));
            }
        }

        int idxRegionId = indexOf(headers, "RegionID");
        int idxRegionName = indexOf(headers, "RegionName");
        int idxRegionType = indexOf(headers, "RegionType");
        int idxStateName = indexOf(headers, "StateName");
        int idxStateCode = indexOf(headers, "State");
        int idxMetro = indexOf(headers, "Metro");
        int idxCounty = indexOf(headers, "CountyName");

        List<ZillowCsvRecord> out = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }

            List<String> cols = ZillowCsvSupport.splitCsvLine(line);
            Map<LocalDate, Double> values = new LinkedHashMap<>();

            for (Map.Entry<Integer, LocalDate> entry : dateColumns.entrySet()) {
                int index = entry.getKey();
                if (index >= cols.size()) {
                    continue;
                }
                String raw = ZillowCsvSupport.normalize(cols.get(index));
                if (!ZillowCsvSupport.hasText(raw)) {
                    continue;
                }
                try {
                    values.put(entry.getValue(), Double.valueOf(raw));
                } catch (NumberFormatException ignored) {
                }
            }

            out.add(new ZillowCsvRecord(
                    get(cols, idxRegionId),
                    get(cols, idxRegionName),
                    normalizeRegionType(get(cols, idxRegionType)),
                    get(cols, idxStateName),
                    get(cols, idxStateCode),
                    get(cols, idxMetro),
                    get(cols, idxCounty),
                    values
            ));
        }

        return out;
    }

    private static String normalizeRegionType(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static int indexOf(List<String> headers, String key) {
        for (int i = 0; i < headers.size(); i++) {
            if (key.equalsIgnoreCase(ZillowCsvSupport.normalize(headers.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private static String get(List<String> cols, int index) {
        if (index < 0 || index >= cols.size()) {
            return null;
        }
        String value = cols.get(index);
        return value == null ? null : value.trim();
    }
}
