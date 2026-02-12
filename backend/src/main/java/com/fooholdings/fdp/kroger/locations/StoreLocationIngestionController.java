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
     * Triggers a fetch + store run using lat/long.
     * Ex: POST /api/kroger/locations/ingest?lat=29.7858&lon=-95.8245&limit=25
     */
    @PostMapping("/ingest")
    public StoreLocationIngestionService.IngestResult ingest(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return ingestionService.ingestByLatLong(lat, lon, limit);
    }


}