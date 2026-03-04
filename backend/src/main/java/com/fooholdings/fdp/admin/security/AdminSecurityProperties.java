package com.fooholdings.fdp.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fdp.admin")
public record AdminSecurityProperties(String apiKey) { }