package com.fooholdings.fdp.kroger.locations;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.kroger.KrogerApiClient;
import com.fooholdings.fdp.kroger.locations.dto.KrogerLocationResponse;

@RestController
@RequestMapping("/api/kroger/locations")
public class KrogerLocationsController {

    private final KrogerApiClient apiClient;

    public KrogerLocationsController(KrogerApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping
    public KrogerLocationResponse search(@RequestParam String zip, @RequestParam(defaultValue = "5") int limit) {
        return apiClient.searchLocations(zip, limit);
    }
}
