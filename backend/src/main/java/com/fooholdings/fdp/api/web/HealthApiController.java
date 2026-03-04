package com.fooholdings.fdp.api.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.HealthResponse;
import com.fooholdings.fdp.core.ingestion.IngestionHealthService;

@RestController
public class HealthApiController {

    private final IngestionHealthService healthService;

    public HealthApiController(IngestionHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public HealthResponse health() {
        return healthService.getHealth();
    }
}