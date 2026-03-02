package com.fooholdings.fdp.api.service;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fooholdings.fdp.api.dto.LocationResponse;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationEntity;
import com.fooholdings.fdp.grocery.location.StoreLocationRepository;
import com.fooholdings.fdp.sources.model.SourceType;

@Service
public class LocationQueryService {

    private final StoreLocationRepository storeLocationRepository;
    private final SourceSystemService sourceSystemService;

    public LocationQueryService(StoreLocationRepository storeLocationRepository,
                                SourceSystemService sourceSystemService) {
        this.storeLocationRepository = storeLocationRepository;
        this.sourceSystemService = sourceSystemService;
    }

    /**
     * Returns up to 200 locations for a zip code, optionally filtered by source.
     */
    public List<LocationResponse> findByZipCode(String zipCode, String source) {
        String zip = normalizeZip(zipCode);

        List<StoreLocationEntity> rows;
        if (source == null || source.isBlank()) {
            rows = storeLocationRepository.findTop200ByPostalCodeOrderByNameAsc(zip);
        } else {
            short sourceSystemId = sourceSystemService.getRequiredIdByCode(parseSource(source).code());
            rows = storeLocationRepository.findTop200BySourceSystemIdAndPostalCodeOrderByNameAsc(sourceSystemId, zip);
        }

        return rows.stream().map(this::toResponse).toList();
    }

    private LocationResponse toResponse(StoreLocationEntity e) {
        String sourceCode = sourceSystemService.getRequiredCodeById(e.getSourceSystemId());
        return new LocationResponse(
                e.getId(),
                sourceCode,
                e.getSourceLocationId(),
                e.getChainCode(),
                e.getName(),
                e.getPhone(),
                e.getAddressLine1(),
                e.getCity(),
                e.getStateCode(),
                e.getPostalCode(),
                e.getCountryCode(),
                e.getGeoRegionId(),
                e.getFirstSeenAt(),
                e.getLastSeenAt()
        );
    }

    private String normalizeZip(String zipCode) {
        if (zipCode == null || zipCode.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "zipCode is required");
        }
        String zip = zipCode.trim();
        // Accept 5-digit ZIP or ZIP+4. We normalize to 5-digit for DB lookup.
        if (zip.matches("^\\d{5}$")) {
            return zip;
        }
        if (zip.matches("^\\d{5}-\\d{4}$")) {
            return zip.substring(0, 5);
        }
        throw new ResponseStatusException(BAD_REQUEST, "zipCode must be 5 digits (or ZIP+4)");
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