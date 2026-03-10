package com.fooholdings.fdp.sources.zillow.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zillow")
public record ZillowProperties(
        Path dataDir,
        String baseUrl,
        int batchSize,
        int downloadRetries
) {
}