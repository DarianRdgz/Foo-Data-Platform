package com.fooholdings.fdp.api.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.PriceResponse;
import com.fooholdings.fdp.api.service.PriceQueryService;

@RestController
@RequestMapping("/api/prices")
public class PriceApiController {

    private final PriceQueryService service;

    public PriceApiController(PriceQueryService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    public List<PriceResponse> history(
            @PathVariable UUID productId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String zipCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String source
    ) {
        return service.getPriceHistory(productId, locationId, zipCode, from, to, source);
    }
}