package com.fooholdings.fdp.api.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.GeoJsonFeatureCollection;
import com.fooholdings.fdp.api.service.MapTileQueryService;

@RestController
@RequestMapping("/api/map/tiles")
public class MapTileApiController {

    private final MapTileQueryService service;

    public MapTileApiController(MapTileQueryService service) {
        this.service = service;
    }

    @GetMapping("/{geoLevel}")
    public ResponseEntity<GeoJsonFeatureCollection> getTiles(@PathVariable String geoLevel,
                                                             @RequestParam String bbox) {
        return ResponseEntity.ok(service.getTiles(geoLevel, bbox));
    }
}