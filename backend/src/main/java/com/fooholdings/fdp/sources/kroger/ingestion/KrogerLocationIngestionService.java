package com.fooholdings.fdp.sources.kroger.ingestion;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.ingestion.IngestionLockService;
import com.fooholdings.fdp.core.ingestion.IngestionRunService;
import com.fooholdings.fdp.core.logging.ErrorCategory;
import com.fooholdings.fdp.core.logging.ErrorCategoryMdc;
import com.fooholdings.fdp.core.logging.IngestionMdc;
import com.fooholdings.fdp.core.source.SourceSystemService;
import com.fooholdings.fdp.grocery.location.StoreLocationJdbcRepository;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiClient;
import com.fooholdings.fdp.sources.kroger.client.KrogerApiException;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse;
import com.fooholdings.fdp.sources.kroger.dto.locations.KrogerLocationResponse.Location;

/**
 * Orchestrates the full lifecycle of a Kroger location ingestion run.
 *
 * Flow:
 *   1. Acquire distributed lock — throws IngestionLockException if already held
 *   2. Start ingestion_run record (lockedBy identifies the caller: "fdp-backend" or "scheduler")
 *   3. Open IngestionMdc scope — all subsequent logs carry ingestion_run_id + source
 *   4. Call Kroger /locations API for each zip code and upsert to store_location
 *   5. Mark run SUCCESS / FAILED
 *   6. Release lock (always in finally)
 *
 * Manual API triggers call ingest(zipCodes) — uses lockedBy="fdp-backend".
 * Scheduled jobs call ingest(zipCodes, "scheduler") — written to ingestion_run.locked_by.
 */
@Service
public class KrogerLocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KrogerLocationIngestionService.class);
    private static final String SOURCE_CODE      = "KROGER";
    private static final String DEFAULT_LOCKED_BY = "fdp-backend";
    private static final Duration LOCK_TTL       = Duration.ofMinutes(20);

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
     * Manual trigger entry point — uses the default lockedBy value.
     *
     * @param zipCodes list of 5-digit zip codes to fetch
     * @return summary message from the completed run
     * @throws IngestionLockException if a run is already in progress
     */
    public String ingest(List<String> zipCodes) {
        return ingest(zipCodes, DEFAULT_LOCKED_BY);
    }

    /**
     * Full entry point — accepts a caller identity written to ingestion_run.locked_by.
     * Scheduled jobs pass lockedBy="scheduler"; manual triggers use the no-arg overload.
     *
     * @param zipCodes list of 5-digit zip codes to fetch
     * @param lockedBy identity of the caller, recorded in ingestion_run
     * @return summary message from the completed run
     * @throws IngestionLockException if a run is already in progress
     */
    public String ingest(List<String> zipCodes, String lockedBy) {
        if (!lockService.tryAcquire(SOURCE_CODE, lockedBy, LOCK_TTL)) {
            throw new IngestionLockException(
                    "Kroger location ingestion is already running. Try again later.");
        }

        UUID runId = null;
        try {
            runId = runService.startRun(
                    SOURCE_CODE,
                    Map.of("type", "locations", "zipCodes", zipCodes),
                    lockedBy
            );

            try (@SuppressWarnings("unused") var ctx = IngestionMdc.withRun(runId, SOURCE_CODE)) {

                int total    = 0;
                int upserted = 0;
                short sourceSystemId = sourceSystemService.getRequiredIdByCode(SOURCE_CODE);

                log.info("Ingestion started: locations (zips={})", zipCodes.size());

                for (String zip : zipCodes) {
                    log.info("Fetching locations for zip={}", zip);

                    KrogerLocationResponse response = apiClient.getLocations(zip, runId);
                    List<Location> locations = response.getData();
                    if (locations == null || locations.isEmpty()) {
                        log.debug("No locations returned for zip={}", zip);
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
                                null,
                                loc.getAddress() != null ? loc.getAddress().getAddressLine1() : null,
                                loc.getAddress() != null ? loc.getAddress().getCity()         : null,
                                loc.getAddress() != null ? loc.getAddress().getState()        : null,
                                loc.getAddress() != null ? loc.getAddress().getZipCode()      : null,
                                "US"
                        );
                        upserted++;
                    }
                }

                String summary = "Processed " + total + " locations across " +
                        zipCodes.size() + " zip(s). Upserted: " + upserted;
                runService.finishSuccess(runId, summary);
                log.info("Ingestion completed: {}", summary);
                return summary;
            }

        } catch (Exception e) {
            if (runId != null) runService.finishFailure(runId, e);

            ErrorCategory category = classify(e);
            try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(category)) {
                if (category == ErrorCategory.LOCK_ERROR || category == ErrorCategory.VALIDATION_ERROR) {
                    log.warn("Ingestion failed: {}", e.getMessage());
                } else {
                    log.error("Ingestion failed: {}", e.getMessage(), e);
                }
            }
            throw e;

        } finally {
            lockService.release(SOURCE_CODE, lockedBy);
        }
    }

    private static ErrorCategory classify(Throwable e) {
        if (e instanceof IngestionLockException)     return ErrorCategory.LOCK_ERROR;
        if (e instanceof KrogerApiException)         return ErrorCategory.API_ERROR;
        if (e instanceof DataAccessException)        return ErrorCategory.DB_ERROR;
        if (e instanceof IllegalArgumentException)   return ErrorCategory.VALIDATION_ERROR;
        return ErrorCategory.UNCLASSIFIED;
    }
}
