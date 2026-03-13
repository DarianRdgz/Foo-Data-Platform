package com.fooholdings.fdp.sources.fema.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fema")
public record FemaProperties(
        String disasterDeclarationsUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {}