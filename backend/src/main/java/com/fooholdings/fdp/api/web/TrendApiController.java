package com.fooholdings.fdp.api.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.TrendResponse;
import com.fooholdings.fdp.api.service.PriceQueryService;

@RestController
@RequestMapping("/api/trends")
public class TrendApiController {

    private final PriceQueryService service;

    public TrendApiController(PriceQueryService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    public TrendResponse productTrend(
            @PathVariable UUID productId,
            @RequestParam(required = false) String period,   // defaults to 30d
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) String source
    ) {
        return service.getProductTrend(productId, period, zipCode, source);
    }

    @GetMapping("/regional")
    public TrendResponse regionalTrend(
            @RequestParam String stateCode,
            @RequestParam UUID productId
    ) {
        return service.getRegionalTrend(stateCode, productId);
    }
}