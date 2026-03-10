package com.fooholdings.fdp.admin.fred;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint — requires the FDP_ADMIN_API_KEY header (enforced by AdminApiKeyInterceptor).
 */
@RestController
@RequestMapping("/api/admin/fred")
public class FredAdminController {

    private final FredSeriesRegistryService registryService;

    public FredAdminController(FredSeriesRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping("/series")
    public List<FredSeriesRegistryRowDto> listSeries() {
        return registryService.listSeries();
    }
}