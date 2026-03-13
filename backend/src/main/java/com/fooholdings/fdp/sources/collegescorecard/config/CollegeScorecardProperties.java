package com.fooholdings.fdp.sources.collegescorecard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "college-scorecard")
public record CollegeScorecardProperties(
        String baseUrl,
        String apiKey,
        String schoolsPath,
        int pageSize,
        int connectTimeoutSeconds,
        int readTimeoutSeconds
) {}