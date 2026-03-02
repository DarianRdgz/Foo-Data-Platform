package com.fooholdings.fdp.api.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fooholdings.fdp.api.dto.ProductResponse;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.product.SourceProductEntity;
import com.fooholdings.fdp.grocery.product.SourceProductRepository;
import com.fooholdings.fdp.sources.model.SourceType;

@Service
public class ProductQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final SourceProductRepository sourceProductRepository;
    private final SourceSystemService sourceSystemService;

    public ProductQueryService(SourceProductRepository sourceProductRepository,
                               SourceSystemService sourceSystemService) {
        this.sourceProductRepository = sourceProductRepository;
        this.sourceSystemService = sourceSystemService;
    }

    public ProductResponse getById(UUID productId) {
        SourceProductEntity entity = sourceProductRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found: " + productId));
        return toResponse(entity);
    }

    /**
     * Search by free-text product name OR UPC-like query.
     *
     * @param q      required, non-blank
     * @param source optional SourceType.code (ex: KROGER)
     */
    public List<ProductResponse> search(String q, String source, Integer limit) {
        String query = normalizeRequiredQuery(q);
        int safeLimit = normalizeLimit(limit);

        Pageable pageable = PageRequest.of(0, safeLimit);

        Short sourceSystemId = (source == null || source.isBlank())
                ? null
                : sourceSystemService.getRequiredIdByCode(parseSource(source).code());

        boolean looksLikeUpc = looksLikeUpc(query);

        List<SourceProductEntity> results;
        if (looksLikeUpc) {
            results = (sourceSystemId == null)
                    ? sourceProductRepository.findByUpc(query, pageable)
                    : sourceProductRepository.findBySourceSystemIdAndUpc(sourceSystemId, query, pageable);
        } else {
            results = (sourceSystemId == null)
                    ? sourceProductRepository.findByNameContainingIgnoreCase(query, pageable)
                    : sourceProductRepository.findBySourceSystemIdAndNameContainingIgnoreCase(sourceSystemId, query, pageable);
        }

        return results.stream().map(this::toResponse).toList();
    }

    private ProductResponse toResponse(SourceProductEntity e) {
        // For now we only have KROGER seeded; mapping below keeps the contract stable as more sources are added.
        String sourceCode = sourceSystemService.getRequiredCodeById(e.getSourceSystemId());
        return new ProductResponse(
            e.getId(),
            sourceCode,
            e.getSourceProductId(),
            e.getUpc(),
            e.getName(),
            e.getBrand(),
            e.getProductPageUri(),
            e.getFirstSeenAt(),
            e.getLastSeenAt()
        );
    }

    private int normalizeLimit(Integer limit) {
        int l = (limit == null) ? DEFAULT_LIMIT : limit;
        if (l < 1 || l > MAX_LIMIT) {
            throw new ResponseStatusException(BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        return l;
    }

    private String normalizeRequiredQuery(String q) {
        if (q == null || q.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "q is required");
        }
        String trimmed = q.trim();
        if (trimmed.length() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "q must be at least 2 characters");
        }
        return trimmed;
    }

    private boolean looksLikeUpc(String q) {
        // UPC-A is 12 digits, but we accept common lengths for GTIN/EAN too.
        if (q.length() < 8 || q.length() > 14) {
            return false;
        }
        for (int i = 0; i < q.length(); i++) {
            if (!Character.isDigit(q.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private SourceType parseSource(String source) {
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        try {
            return SourceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown source: " + source);
        }
    }
}