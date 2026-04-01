package com.fooholdings.fdp.api.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.AreaResponse;
import com.fooholdings.fdp.api.service.AreaQueryService;

/**
 * Public read API for geographic area data.
 *
 * Story 5.4 scope: latest snapshots only.
 * Story 5.5 will add: /history, /children, /search endpoints.
 */
@RestController
@RequestMapping("/api/area")
public class AreaApiController {

    private final AreaQueryService service;

    public AreaApiController(AreaQueryService service) {
        this.service = service;
    }

    /**
     * Returns geo metadata and the latest snapshot for each data category
     * for the given geographic unit.
     *
     * @param geoLevel one of: national, state, metro, county, city, zip
     * @param geoId    UUID from fdp_geo.geo_areas
     * @return 200 with AreaResponse, or 404 if the geo unit does not exist
     */
    @GetMapping("/{geoLevel}/{geoId}")
    public ResponseEntity<AreaResponse> getArea(
            @PathVariable String geoLevel,
            @PathVariable UUID geoId
    ) {
        return ResponseEntity.ok(service.getArea(geoLevel, geoId));
    }
}