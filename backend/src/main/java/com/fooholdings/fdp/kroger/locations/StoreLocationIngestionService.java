package com.fooholdings.fdp.kroger.locations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.kroger.KrogerApiClient;
import com.fooholdings.fdp.kroger.locations.dto.KrogerLocationResponse;

@Service
public class StoreLocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StoreLocationIngestionService.class);

    private final KrogerApiClient krogerApiClient;
    private final StoreLocationRepository repository;

    public StoreLocationIngestionService(KrogerApiClient krogerApiClient, StoreLocationRepository repository) {
        this.krogerApiClient = krogerApiClient;
        this.repository = repository;
    }

    public IngestResult ingestByZip(String zip, int limit) {
        KrogerLocationResponse resp = krogerApiClient.searchLocations(zip, limit);

        List<KrogerLocationResponse.Location> locations =
                (resp == null || resp.getData() == null) ? List.of() : resp.getData();

        log.info("Kroger locations fetched: zip={}, count={}", zip, locations.size());

        if (locations.isEmpty()) {
            // Acceptance criteria: log it and continue
            return new IngestResult(zip, 0, 0);
        }

        Instant now = Instant.now();
        List<StoreLocationEntity> entities = new ArrayList<>();

        for (KrogerLocationResponse.Location loc : locations) {
            if (loc == null || loc.getLocationId() == null || loc.getLocationId().isBlank()) {
                continue;
            }

            StoreLocationEntity e = new StoreLocationEntity();
            e.setLocationId(loc.getLocationId());
            e.setName(loc.getName());

            if (loc.getAddress() != null) {
                e.setAddressLine1(loc.getAddress().getAddressLine1());
                e.setCity(loc.getAddress().getCity());
                e.setState(loc.getAddress().getState());
                e.setZipCode(loc.getAddress().getZipCode());
            }

            repository.findById(e.getLocationId()).ifPresentOrElse(existing -> {
                e.setCreatedAt(existing.getCreatedAt());
            }, () -> {
                e.setCreatedAt(now);
            });

            e.setUpdatedAt(now);
            entities.add(e);
        }

        List<StoreLocationEntity> saved = repository.saveAll(entities);

        return new IngestResult(zip, locations.size(), saved.size());
    }

    public IngestResult ingestByLatLong(double lat, double lon, int limit) {
        KrogerLocationResponse resp = krogerApiClient.searchLocationsNear(lat, lon, limit);

        List<KrogerLocationResponse.Location> locations =
                (resp == null || resp.getData() == null) ? List.of() : resp.getData();

        log.info("Kroger locations fetched: lat={}, lon={}, count={}", lat, lon, locations.size());

        if (locations.isEmpty()) {
            return new IngestResult(null, 0, 0);
        }

        Instant now = Instant.now();
        List<StoreLocationEntity> entities = new ArrayList<>();

        for (KrogerLocationResponse.Location loc : locations) {
            if (loc == null || loc.getLocationId() == null || loc.getLocationId().isBlank()) {
                continue;
            }

            StoreLocationEntity e = new StoreLocationEntity();
            e.setLocationId(loc.getLocationId());
            e.setName(loc.getName());

            if (loc.getAddress() != null) {
                e.setAddressLine1(loc.getAddress().getAddressLine1());
                e.setCity(loc.getAddress().getCity());
                e.setState(loc.getAddress().getState());
                e.setZipCode(loc.getAddress().getZipCode());
            }

            repository.findById(e.getLocationId()).ifPresentOrElse(existing -> {
                e.setCreatedAt(existing.getCreatedAt());
            }, () -> {
                e.setCreatedAt(now);
            });

            e.setUpdatedAt(now);
            entities.add(e);
        }

        List<StoreLocationEntity> saved = repository.saveAll(entities);
        return new IngestResult(null, locations.size(), saved.size());
    }


    public record IngestResult(String zip, int fetched, int saved) {}
}