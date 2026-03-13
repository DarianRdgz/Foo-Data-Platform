package com.fooholdings.fdp.sources.noaa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "noaa")
public record NoaaProperties(
        String stormEventsBaseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {}