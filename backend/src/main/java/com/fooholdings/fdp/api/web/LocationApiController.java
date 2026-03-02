package com.fooholdings.fdp.api.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.LocationResponse;
import com.fooholdings.fdp.api.service.LocationQueryService;

@RestController
@RequestMapping("/api/locations")
public class LocationApiController {

    private final LocationQueryService locationQueryService;

    public LocationApiController(LocationQueryService locationQueryService) {
        this.locationQueryService = locationQueryService;
    }

    @GetMapping
    public List<LocationResponse> locations(
            @RequestParam(name = "zipCode") String zipCode,
            @RequestParam(name = "source", required = false) String source
    ) {
        return locationQueryService.findByZipCode(zipCode, source);
    }
}