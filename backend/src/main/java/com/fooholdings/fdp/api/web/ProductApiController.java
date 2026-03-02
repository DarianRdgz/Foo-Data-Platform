package com.fooholdings.fdp.api.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fooholdings.fdp.api.dto.ProductResponse;
import com.fooholdings.fdp.api.service.ProductQueryService;

@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductQueryService productQueryService;

    public ProductApiController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/{productId}")
    public ProductResponse getProduct(@PathVariable UUID productId) {
        return productQueryService.getById(productId);
    }

    @GetMapping("/search")
    public List<ProductResponse> searchProducts(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        return productQueryService.search(q, source, limit);
    }
}