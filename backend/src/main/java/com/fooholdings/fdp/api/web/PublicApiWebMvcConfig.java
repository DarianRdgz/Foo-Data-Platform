package com.fooholdings.fdp.api.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PublicApiWebMvcConfig implements WebMvcConfigurer {

    private final PublicApiCorsProperties corsProperties;

    public PublicApiWebMvcConfig(PublicApiCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (corsProperties.getAllowedOrigins() == null || corsProperties.getAllowedOrigins().isEmpty()) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
