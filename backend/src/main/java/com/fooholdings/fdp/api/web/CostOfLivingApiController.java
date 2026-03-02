package com.fooholdings.fdp.api.web;

import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.StapleZipBasketResponse;
import com.fooholdings.fdp.api.service.PriceQueryService;

@RestController
@RequestMapping("/api/cost-of-living/grocery")
public class CostOfLivingApiController {

    private final PriceQueryService service;

    public CostOfLivingApiController(PriceQueryService service) {
        this.service = service;
    }

    @GetMapping("/staples/zip-averages")
    public List<StapleZipBasketResponse> stapleZipAverages(
            @RequestParam(required = false) String stateCode,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String source
    ) {
        return service.getStapleBasketByZip(stateCode, period, from, to, source);
    }
}