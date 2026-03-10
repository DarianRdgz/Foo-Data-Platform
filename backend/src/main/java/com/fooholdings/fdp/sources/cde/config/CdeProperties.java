package com.fooholdings.fdp.sources.cde.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized config for the FBI Crime Data Explorer adapter.
 * baseUrl and paths are environment-overridable for local development and CI stubbing.
 */
@ConfigurationProperties(prefix = "cde")
public record CdeProperties(
        String baseUrl,
        String apiKey,
        String offensesKnownPath,
        String clearancesPath,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {
        public CdeProperties {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "CDE_API_KEY is not set. Register at https://api.data.gov/signup/");
        }
    }
}