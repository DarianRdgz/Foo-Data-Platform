package com.fooholdings.fdp.sources.kroger.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.sources.kroger.ingestion.KrogerLocationIngestionService;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerProductIngestionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Trigger endpoints for Kroger data ingestion.
 * Consolidates location and product ingestion under /kroger/ingestion.
 *
 * All endpoints are synchronous
 *
 * POST /kroger/ingestion/locations  — triggers location ingestion for given zip codes
 * POST /kroger/ingestion/products   — triggers product/price ingestion for given locations + terms
 */
@RestController
@RequestMapping("/kroger/ingestion")
public class KrogerIngestionController {

    private final KrogerLocationIngestionService locationIngestionService;
    private final KrogerProductIngestionService productIngestionService;

    public KrogerIngestionController(KrogerLocationIngestionService locationIngestionService,
                                     KrogerProductIngestionService productIngestionService) {
        this.locationIngestionService = locationIngestionService;
        this.productIngestionService = productIngestionService;
    }

    /**
     * Triggers location ingestion for the supplied zip codes.
     *
     * Request body: { "zipCodes": ["77001", "77002"] }
     */
    @PostMapping("/locations")
    public ResponseEntity<Map<String, Object>> triggerLocations(
            @Valid @RequestBody LocationIngestionRequest request) {
        try {
            String summary = locationIngestionService.ingest(request.zipCodes());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "summary", summary));
        } catch (IllegalStateException e) {
            // Lock contention
            return ResponseEntity.status(409).body(Map.of("status", "LOCKED", "message", e.getMessage()));
        }
    }

    /**
     * Triggers product and price ingestion for the supplied location IDs and search terms.
     *
     * Request body: { "locationIds": ["70100277"], "searchTerms": ["milk", "bread"] }
     */
    @PostMapping("/products")
    public ResponseEntity<Map<String, Object>> triggerProducts(
            @Valid @RequestBody ProductIngestionRequest request) {
        try {
            String summary = productIngestionService.ingest(request.locationIds(), request.searchTerms());
            return ResponseEntity.ok(Map.of("status", "SUCCESS", "summary", summary));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("status", "LOCKED", "message", e.getMessage()));
        }
    }

    // Request records

    public record LocationIngestionRequest(
            @NotEmpty(message = "At least one zip code is required")
            List<String> zipCodes
    ) {}

    public record ProductIngestionRequest(
            @NotEmpty(message = "At least one locationId is required")
            List<String> locationIds,
            @NotEmpty(message = "At least one search term is required")
            List<String> searchTerms
    ) {}
}
