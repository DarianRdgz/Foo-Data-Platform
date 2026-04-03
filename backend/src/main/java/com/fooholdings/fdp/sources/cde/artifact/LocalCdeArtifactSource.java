package com.fooholdings.fdp.sources.cde.artifact;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.core.ingestion.SourceArtifactJdbcRepository;
import com.fooholdings.fdp.sources.cde.config.CdeProperties;

@Component
@ConditionalOnProperty(prefix = "cde", name = "mode", havingValue = "LOCAL", matchIfMissing = true)
public class LocalCdeArtifactSource implements CdeArtifactSource {

    private static final Logger log = LoggerFactory.getLogger(LocalCdeArtifactSource.class);
    private static final String SOURCE = "CDE";
    private static final String SUPPORTED_COLLECTION = "offenses_known_to_law_enforcement";
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "^(?<collection>[a-z0-9_]+)__(?<year>\\d{4})__(?<state>[a-z0-9_]+)\\.(?<ext>csv|zip)$"
    );

    private final CdeProperties props;
    private final SourceArtifactJdbcRepository artifactRepo;

    public LocalCdeArtifactSource(CdeProperties props, SourceArtifactJdbcRepository artifactRepo) {
        this.props = props;
        this.artifactRepo = artifactRepo;
    }

    @Override
    public List<CdeArtifact> discoverPendingArtifacts() {
        try {
            Files.createDirectories(props.downloadDirPath());
            Files.createDirectories(props.workDirPath());

            try (var paths = Files.list(props.downloadDirPath())) {
                paths.filter(Files::isRegularFile)
                        .filter(this::isSupportedArtifact)
                        .forEach(this::registerIfNew);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan CDE download directory", e);
        }

        return artifactRepo.findPendingBySourceCode(SOURCE).stream()
                .map(row -> new CdeArtifact(
                        row.id(),
                        row.collectionCode(),
                        row.stateCode(),
                        row.artifactYear(),
                        row.originalFilename(),
                        row.storagePath()))
                .toList();
    }

    private boolean isSupportedArtifact(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".csv") || fileName.endsWith(".zip");
    }

    private void registerIfNew(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        Matcher matcher = FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            log.warn("CDE: skipping inbox file with invalid normalized name: {}", fileName);
            return;
        }

        String collection = matcher.group("collection");
        if (!SUPPORTED_COLLECTION.equals(collection)) {
            log.info("CDE: skipping unsupported collection {} in file {}", collection, fileName);
            return;
        }

        String sha256 = sha256(path);
        if (artifactRepo.existsBySha256(SOURCE, sha256)) {
            return;
        }

        artifactRepo.insertDiscovered(
                SOURCE,
                "DOWNLOAD",
                collection,
                matcher.group("state"),
                Integer.valueOf(matcher.group("year")),
                path.getFileName().toString(),
                path,
                null,
                sha256,
                fileSize(path)
        );
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file size for " + path, e);
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path); DigestInputStream dis = new DigestInputStream(in, digest)) {
                dis.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to hash CDE artifact " + path, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
