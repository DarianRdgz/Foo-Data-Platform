package com.fooholdings.fdp.kroger.products;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kroger/products")
public class ProductIngestionController {

    private final ProductIngestionService ingestionService;

    public ProductIngestionController(ProductIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Example:
     * POST /api/kroger/products/ingest?locationId=...&term=milk&limit=10&start=0
     */
    @PostMapping("/ingest")
    public ProductIngestionService.IngestResult ingest(
            @RequestParam String locationId,
            @RequestParam String term,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int start
    ) {
        return ingestionService.ingest(locationId, term, limit, start);
    }
}
