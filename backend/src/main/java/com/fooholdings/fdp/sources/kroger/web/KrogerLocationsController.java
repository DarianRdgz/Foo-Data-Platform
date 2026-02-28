package com.fooholdings.fdp.sources.kroger.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationEntity;
import com.fooholdings.fdp.grocery.location.StoreLocationRepository;

/**
 * Read-only query endpoints for ingested Kroger store locations.
 *
 * GET /kroger/locations           — all Kroger locations in the DB
 * GET /kroger/locations/{locId}   — single location by Kroger location ID
 */
@RestController
@RequestMapping("/kroger/locations")
public class KrogerLocationsController {

    private final StoreLocationRepository locationRepo;
    private final SourceSystemService sourceSystemService;

    public KrogerLocationsController(StoreLocationRepository locationRepo,
                                     SourceSystemService sourceSystemService) {
        this.locationRepo = locationRepo;
        this.sourceSystemService = sourceSystemService;
    }

    @GetMapping
    public ResponseEntity<List<StoreLocationEntity>> all() {
        short krogerSourceId = sourceSystemService.getRequiredIdByCode("KROGER");
        List<StoreLocationEntity> locations = locationRepo.findAll().stream()
                .filter(l -> l.getSourceSystemId() == krogerSourceId)
                .toList();
        return ResponseEntity.ok(locations);
    }

    @GetMapping("/{locationId}")
    public ResponseEntity<?> byLocationId(@PathVariable String locationId) {
        short krogerSourceId = sourceSystemService.getRequiredIdByCode("KROGER");
        return locationRepo.findBySourceSystemIdAndSourceLocationId(krogerSourceId, locationId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
