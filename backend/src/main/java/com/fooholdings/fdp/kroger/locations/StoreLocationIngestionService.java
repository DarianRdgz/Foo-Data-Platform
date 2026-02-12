package com.fooholdings.fdp.kroger.locations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.ingestion.IngestionRunService;
import com.fooholdings.fdp.kroger.KrogerApiClient;
import com.fooholdings.fdp.kroger.locations.dto.KrogerLocationResponse;

@Service
public class StoreLocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(StoreLocationIngestionService.class);

    private final KrogerApiClient krogerApiClient;
    private final StoreLocationRepository repository;
    private final IngestionRunService runService;


    public StoreLocationIngestionService(KrogerApiClient krogerApiClient, StoreLocationRepository repository, com.fooholdings.fdp.ingestion.IngestionRunService runService) {
        this.krogerApiClient = krogerApiClient;
        this.repository = repository;
        this.runService = runService;
    }
    
    public IngestResult ingestByLatLong(double lat, double lon, int limit) {

        Long runId = runService.startRun(
            "kroger.locations",
            "lat=" + lat + ", lon=" + lon + ", limit=" + limit);

        try {
                KrogerLocationResponse resp = krogerApiClient.searchLocationsNear(lat, lon, limit);

                List<KrogerLocationResponse.Location> locations =
                        (resp == null || resp.getData() == null) ? List.of() : resp.getData();

                log.info("Kroger locations fetched: lat={}, lon={}, count={}", lat, lon, locations.size());

                if (locations.isEmpty()) {
                    IngestResult result = new IngestResult(null, 0, 0);
                    runService.endSuccess(runId, "fetched=0, saved=0");
                    return result;
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

                IngestResult result = new IngestResult(null, locations.size(), saved.size());
                runService.endSuccess(runId, "fetched=" + result.fetched() + ", saved=" + result.saved());

                return result;

            }catch (Exception ex) {
                runService.endFailure(runId, ex);
                throw ex;
            }
        }

        public record IngestResult(String zip, int fetched, int saved) {}
}