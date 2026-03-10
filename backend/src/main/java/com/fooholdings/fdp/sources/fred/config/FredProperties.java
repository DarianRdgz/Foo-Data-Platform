package com.fooholdings.fdp.sources.fred.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection-level config for the FRED API adapter.
 * Series catalog lives in fred-series-catalog.yml, not here.
 */
@ConfigurationProperties(prefix = "fred")
public record FredProperties(
        String apiKey,
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        RateLimiter rateLimiter
) {
    public record RateLimiter(int limitForPeriodPerMinute, long timeoutMillis) {}
}