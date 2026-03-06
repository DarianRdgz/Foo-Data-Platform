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
import com.fooholdings.fdp.sources.adapter.GrocerySourceAdapter;
import com.fooholdings.fdp.sources.adapter.GrocerySourceAdapterRegistry;
import com.fooholdings.fdp.sources.model.CanonicalLocation;
import com.fooholdings.fdp.sources.model.LocationQuery;
import com.fooholdings.fdp.sources.model.SourceType;

/**
 * Orchestrates the full lifecycle of a Kroger location ingestion run.
 */
@Service
public class KrogerLocationIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KrogerLocationIngestionService.class);
    private static final String SOURCE_CODE = "KROGER";
    private static final String DEFAULT_LOCKED_BY = "fdp-backend";
    private static final Duration LOCK_TTL = Duration.ofMinutes(20);

    private final GrocerySourceAdapter adapter;
    private final StoreLocationJdbcRepository locationRepo;
    private final IngestionRunService runService;
    private final IngestionLockService lockService;
    private final SourceSystemService sourceSystemService;

    public KrogerLocationIngestionService(GrocerySourceAdapterRegistry adapterRegistry,
                                          StoreLocationJdbcRepository locationRepo,
                                          IngestionRunService runService,
                                          IngestionLockService lockService,
                                          SourceSystemService sourceSystemService) {
        this.adapter = adapterRegistry.getRequired(SourceType.KROGER);
        this.locationRepo = locationRepo;
        this.runService = runService;
        this.lockService = lockService;
        this.sourceSystemService = sourceSystemService;
    }

    public String ingest(List<String> zipCodes) {
        return ingest(zipCodes, DEFAULT_LOCKED_BY);
    }

    public String ingest(List<String> zipCodes, String lockedBy) {
        if (!lockService.tryAcquire(SOURCE_CODE, lockedBy, LOCK_TTL)) {
            throw new IngestionLockException("Kroger location ingestion is already running. Try again later.");
        }

        UUID runId = null;
        try {
            runId = runService.startRun(
                    SOURCE_CODE,
                    Map.of("type", "locations", "zipCodes", zipCodes),
                    lockedBy
            );

            try (@SuppressWarnings("unused") var ctx = IngestionMdc.withRun(runId, SOURCE_CODE)) {

                short sourceSystemId = sourceSystemService.getRequiredIdByCode(SOURCE_CODE);

                log.info("Ingestion started: locations (zips={})", (zipCodes == null ? 0 : zipCodes.size()));

                LocationQuery query = new LocationQuery(zipCodes, runId);
                List<CanonicalLocation> locations = adapter.fetchLocations(query);

                int total = 0;
                int upserted = 0;

                for (CanonicalLocation loc : locations) {
                    if (loc == null) continue;
                    total++;

                    locationRepo.upsert(
                            UUID.randomUUID(),
                            sourceSystemId,
                            loc.sourceLocationId(),
                            loc.chainCode(),
                            loc.name(),
                            loc.phone(),
                            loc.addressLine1(),
                            loc.city(),
                            loc.stateCode(),
                            loc.postalCode(),
                            loc.countryCode()
                    );
                    upserted++;
                }

                String summary = "Processed " + total + " locations. Upserted: " + upserted;
                runService.finishSuccess(runId, upserted, summary);
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
        if (e instanceof IngestionLockException) return ErrorCategory.LOCK_ERROR;
        if (e instanceof DataAccessException) return ErrorCategory.DB_ERROR;
        if (e instanceof IllegalArgumentException) return ErrorCategory.VALIDATION_ERROR;
        return ErrorCategory.UNCLASSIFIED;
    }
}