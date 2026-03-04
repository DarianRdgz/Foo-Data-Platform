package com.fooholdings.fdp.admin.ingestion;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fdp.quota")
public record FdpQuotaProperties(Map<String, Integer> dailyLimits) { }