package com.fooholdings.fdp.sources.kroger.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationJdbcRepository;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse.Location;

/**
 * Orchestrates the full lifecycle of a Kroger location ingestion run.
 *
 * Flow:
 *   1. Acquire distributed ingestion lock (prevents concurrent runs)
 *   2. Start ingestion_run record in RUNNING state
 *   3. Call Kroger /locations API for each zip code
 *   4. Upsert each location to fdp_grocery.store_location
 *   5. Mark run SUCCESS
 *   6. Release lock (always in finally)
 *
 * If any step throws, the run is marked FAILED and the lock is released.
 * The raw payload is always saved by KrogerApiClient before exceptions propagate.
 *
 * Lock TTL is set to 20 minutes. The typical location run completes in under
 * 2 minutes for a small zip list. If the process crashes mid-run the lock
 * will auto-expire and the next invocation will succeed.
 */
@Service
public class KrogerLocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KrogerLocationIngestionService.class);
    private static final String SOURCE_CODE = "KROGER";
    private static final String LOCKED_BY = "fdp-backend";
    private static final Duration LOCK_TTL = Duration.ofMinutes(20);

    private final KrogerApiClient apiClient;
    private final StoreLocationJdbcRepository locationRepo;
    private final IngestionRunService runService;
    private final IngestionLockService lockService;
    private final SourceSystemService sourceSystemService;

    public KrogerLocationIngestionService(KrogerApiClient apiClient,
                                          StoreLocationJdbcRepository locationRepo,
                                          IngestionRunService runService,
                                          IngestionLockService lockService,
                                          SourceSystemService sourceSystemService) {
        this.apiClient = apiClient;
        this.locationRepo = locationRepo;
        this.runService = runService;
        this.lockService = lockService;
        this.sourceSystemService = sourceSystemService;
    }

    /**
     * Ingests Kroger store locations for the given zip codes.
     *
     * @param zipCodes list of 5-digit zip codes to fetch (e.g. ["77001", "77002"])
     * @return summary message from the completed run
     * @throws IllegalStateException if the ingestion lock cannot be acquired
     */
    public String ingest(List<String> zipCodes) {
        if (!lockService.tryAcquire(SOURCE_CODE, LOCKED_BY, LOCK_TTL)) {
            throw new IllegalStateException(
                    "Kroger location ingestion is already running. Try again later.");
        }

        UUID runId = null;
        try {
            runId = runService.startRun(
                    SOURCE_CODE,
                    Map.of("type", "locations", "zipCodes", zipCodes),
                    LOCKED_BY
            );

            int total = 0;
            int upserted = 0;
            short sourceSystemId = sourceSystemService.getRequiredIdByCode(SOURCE_CODE);

            for (String zip : zipCodes) {
                log.info("[run:{}] Fetching locations for zip={}", runId, zip);
                KrogerLocationResponse response = apiClient.getLocations(zip, runId);
                List<Location> locations = response.getData();
                if (locations == null || locations.isEmpty()) {
                    log.debug("[run:{}] No locations returned for zip={}", runId, zip);
                    continue;
                }

                for (Location loc : locations) {
                    total++;
                    locationRepo.upsert(
                            UUID.randomUUID(),
                            sourceSystemId,
                            loc.getLocationId(),
                            "KROGER",
                            loc.getName(),
                            null, // phone not in current DTO
                            loc.getAddress() != null ? loc.getAddress().getAddressLine1() : null,
                            loc.getAddress() != null ? loc.getAddress().getCity() : null,
                            loc.getAddress() != null ? loc.getAddress().getState() : null,
                            loc.getAddress() != null ? loc.getAddress().getZipCode() : null,
                            "US"
                    );
                    upserted++;
                }
            }

            String summary = "Processed " + total + " locations across " + zipCodes.size() + " zip(s). Upserted: " + upserted;
            runService.finishSuccess(runId, summary);
            return summary;

        } catch (Exception e) {
            if (runId != null) runService.finishFailure(runId, e);
            throw e;
        } finally {
            lockService.release(SOURCE_CODE, LOCKED_BY);
        }
    }
}
