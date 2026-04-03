package com.fooholdings.fdp.sources.cde.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cde")
public record CdeProperties(
        Mode mode,
        String downloadPageUrl,
        String downloadDir,
        String workDir,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
    public enum Mode {
        LOCAL,
        PORTAL_DOWNLOAD
    }

    public CdeProperties {
        mode = mode == null ? Mode.LOCAL : mode;
        if (downloadDir == null || downloadDir.isBlank()) {
            throw new IllegalStateException("cde.download-dir must be configured");
        }
        if (workDir == null || workDir.isBlank()) {
            throw new IllegalStateException("cde.work-dir must be configured");
        }
    }

    public Path downloadDirPath() {
        return Path.of(downloadDir);
    }

    public Path workDirPath() {
        return Path.of(workDir);
    }
}
