package com.fooholdings.fdp.kroger.locations;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kroger/locations")
public class StoreLocationIngestionController {

    private final StoreLocationIngestionService ingestionService;

    public StoreLocationIngestionController(StoreLocationIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Triggers a fetch + store run.
     * Ex: POST /api/kroger/locations/ingest?zip=77002&limit=25
     */
    @PostMapping("/ingest")
    public StoreLocationIngestionService.IngestResult ingest(
            @RequestParam String zip,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return ingestionService.ingestByZip(zip, limit);
    }

    @PostMapping("/ingest-near")
    public StoreLocationIngestionService.IngestResult ingestNear(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return ingestionService.ingestByLatLong(lat, lon, limit);
    }

}