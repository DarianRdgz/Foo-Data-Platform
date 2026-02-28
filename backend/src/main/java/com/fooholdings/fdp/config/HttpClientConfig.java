package com.fooholdings.fdp.config;

import org.springframework.context.annotation.Configuration;

/**
 * Application-wide HTTP client configuration.
 *
 * In Spring Boot 4, spring-boot-starter-restclient auto configures a
 * RestClient.Builder bean with sane defaults. Each source adapter
 * injects that builder and constructs its own RestClient instance, which 
 * allows per-source base URLs and timeouts without sharing state.
 *
 * Global customizations belong here as 
 * RestClientBuilderCustomizer @Bean methods. None are needed yet,
 * but the class is kept as the canonical location for future additions.
 *
 * Spring Boot 4 http client timeout properties:
 *   spring.http.clients.connect-timeout=5s
 *   spring.http.clients.read-timeout=30s
 * These can also be set in application.yml instead of code.
 */

@Configuration
public class HttpClientConfig {
    // Add RestClientBuilderCustomizer beans in the future to avoid cross-cutting problems
}
