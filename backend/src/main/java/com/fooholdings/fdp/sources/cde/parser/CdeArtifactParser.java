package com.fooholdings.fdp.sources.cde.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fooholdings.fdp.sources.cde.artifact.CdeArtifact;
import com.fooholdings.fdp.sources.cde.config.CdeProperties;

@Component
public class CdeArtifactParser {

    private final CdeProperties props;

    public CdeArtifactParser(CdeProperties props) {
        this.props = props;
    }

    public List<CdeCrimeRow> parse(CdeArtifact artifact) {
        Path csvPath = materializeCsv(artifact);

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            Map<String, Integer> headers = new HashMap<>();
            parser.getHeaderMap().forEach((k, v) -> headers.put(normalize(k), v));

            String yearHeader = firstPresent(headers, "year")
                    .orElseThrow(() -> new IllegalStateException("CDE artifact has no year column: " + artifact.originalFilename()));
            String stateHeader = firstPresent(headers, "state", "state_name")
                    .orElseThrow(() -> new IllegalStateException("CDE artifact has no state column: " + artifact.originalFilename()));

            String violentHeader = firstPresent(headers, "violent_crime_rate", "violent_rate").orElse(null);
            String propertyHeader = firstPresent(headers, "property_crime_rate", "property_rate").orElse(null);
            String totalHeader = firstPresent(headers, "actual", "offense_count", "incidents").orElse(null);
            String per100kHeader = firstPresent(headers, "rate", "offenses_per_100000", "per_100000").orElse(null);

            List<CdeCrimeRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                rows.add(new CdeCrimeRow(
                        value(record, yearHeader),
                        value(record, stateHeader),
                        parseDouble(value(record, violentHeader)),
                        parseDouble(value(record, propertyHeader)),
                        parseDouble(value(record, totalHeader)),
                        parseDouble(value(record, per100kHeader))
                ));
            }
            return rows;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse CDE artifact " + artifact.originalFilename(), e);
        }
    }

    private Path materializeCsv(CdeArtifact artifact) {
        String filename = artifact.storagePath().getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".csv")) {
            return artifact.storagePath();
        }
        if (filename.endsWith(".zip")) {
            return extractBestCsv(artifact);
        }
        throw new IllegalArgumentException("Unsupported CDE artifact type: " + artifact.storagePath());
    }

    private Path extractBestCsv(CdeArtifact artifact) {
        try {
            Files.createDirectories(props.workDirPath());

            try (ZipFile zip = new ZipFile(artifact.storagePath().toFile(), StandardCharsets.UTF_8)) {
                List<? extends ZipEntry> csvEntries = zip.stream()
                        .filter(entry -> !entry.isDirectory())
                        .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted(Comparator.comparing(ZipEntry::getName))
                        .toList();

                if (csvEntries.isEmpty()) {
                    throw new IllegalStateException("No CSV entry found inside zip artifact " + artifact.originalFilename());
                }

                ZipEntry chosen = chooseEntry(csvEntries, artifact.collectionCode());
                String extractedName = artifact.artifactId() + "__" + Path.of(chosen.getName()).getFileName();
                Path extracted = props.workDirPath().resolve(extractedName);

                try (var in = zip.getInputStream(chosen)) {
                    Files.copy(in, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return extracted;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract CSV from " + artifact.originalFilename(), e);
        }
    }

    private ZipEntry chooseEntry(List<? extends ZipEntry> entries, String collectionCode) {
        return entries.stream()
                .sorted(Comparator.comparingInt((ZipEntry entry) -> score(entry.getName(), collectionCode)).reversed()
                        .thenComparing(ZipEntry::getName))
                .findFirst()
                .orElseThrow();
    }

    private int score(String entryName, String collectionCode) {
        String normalized = entryName.toLowerCase(Locale.ROOT);
        int score = 0;
        if (normalized.contains(collectionCode)) {
            score += 100;
        }
        if (normalized.contains("offense")) {
            score += 50;
        }
        if (normalized.contains("state")) {
            score += 10;
        }
        return score;
    }

    private Optional<String> firstPresent(Map<String, Integer> headers, String... candidates) {
        for (String candidate : candidates) {
            if (headers.containsKey(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private String value(CSVRecord record, String header) {
        if (header == null || !record.isMapped(header)) {
            return null;
        }
        String value = record.get(header);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Double parseDouble(String raw) {
        if (!StringUtils.hasText(raw) || "NULL".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return NumberFormat.getNumberInstance(Locale.US).parse(raw).doubleValue();
        } catch (ParseException ex) {
            return null;
        }
    }

    private String normalize(String raw) {
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
