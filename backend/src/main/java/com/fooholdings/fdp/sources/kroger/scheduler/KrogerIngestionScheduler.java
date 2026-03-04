package com.fooholdings.fdp.sources.kroger.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.admin.jobs.JobDefinition;
import com.fooholdings.fdp.admin.jobs.JobRegistry;
import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.logging.ErrorCategory;
import com.fooholdings.fdp.core.logging.ErrorCategoryMdc;
import com.fooholdings.fdp.core.scheduler.FdpSchedulerProperties;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerLocationIngestionService;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerProductIngestionService;

import jakarta.annotation.PostConstruct;

/**
 * Scheduled ingestion jobs for the Kroger source.
 *
 * The bean always exists so an admin dashboard can inspect and toggle
 * it without an app restart.
 *
 * Runtime enabled/disabled is controlled by JobRegistry (Story 4.2).
 * fdp.scheduler.kroger.enabled only seeds the initial state at startup.
 *
 * Both jobs use lockedBy="scheduler" so ingestion_run records are clearly
 * distinguishable from manual trigger runs (lockedBy="fdp-backend").
 *
 * Lock contention is treated as INFO, not an error — it means a manually
 * triggered run or a previous scheduled run is still in progress.
 */
@Component
public class KrogerIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(KrogerIngestionScheduler.class);

    private static final String LOCKED_BY = "scheduler";
    private static final String JOB_LOCATIONS = "kroger-locations";
    private static final String JOB_PRODUCTS  = "kroger-products";

    private final JobRegistry jobRegistry;
    private final FdpSchedulerProperties props;
    private final KrogerLocationIngestionService locationIngestion;
    private final KrogerProductIngestionService productIngestion;

    public KrogerIngestionScheduler(JobRegistry jobRegistry,
                                    FdpSchedulerProperties props,
                                    KrogerLocationIngestionService locationIngestion,
                                    KrogerProductIngestionService productIngestion) {
        this.jobRegistry = jobRegistry;
        this.props = props;
        this.locationIngestion = locationIngestion;
        this.productIngestion = productIngestion;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerJobs() {
        jobRegistry.register(
                new JobDefinition(
                        JOB_LOCATIONS,
                        "KROGER",
                        () -> props.kroger().locations().cron(),
                        () -> props.kroger().enabled()
                ),
                this::runLocationsInternal
        );

        jobRegistry.register(
                new JobDefinition(
                        JOB_PRODUCTS,
                        "KROGER",
                        () -> props.kroger().products().cron(),
                        () -> props.kroger().enabled()
                ),
                this::runProductsInternal
        );
    }

    @Scheduled(cron = "${fdp.scheduler.kroger.locations.cron}")
    public void runLocations() {
        jobRegistry.runScheduled(JOB_LOCATIONS);
    }

    @Scheduled(cron = "${fdp.scheduler.kroger.products.cron}")
    public void runProducts() {
        jobRegistry.runScheduled(JOB_PRODUCTS);
    }

    private void runLocationsInternal() {
        List<String> zipCodes = safeList(props.kroger().locations().zipCodes());
        if (zipCodes.isEmpty()) {
            log.warn("Scheduler: skipping Kroger locations run — no zipCodes configured.");
            return;
        }

        log.info("Scheduler: starting Kroger locations ingestion — zipCount={}", zipCodes.size());
        try {
            String summary = locationIngestion.ingest(zipCodes, LOCKED_BY);
            log.info("Scheduler: Kroger locations ingestion complete — {}", summary);
        } catch (IngestionLockException e) {
            log.info("Scheduler: Kroger locations run skipped — lock already held: {}", e.getMessage());
        } catch (Exception e) {
            try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
                log.error("Scheduler: Kroger locations run failed unexpectedly: {}", e.getMessage(), e);
            }
        }
    }

    private void runProductsInternal() {
        List<String> locationIds = safeList(props.kroger().products().locationIds());
        List<String> searchTerms = safeList(props.kroger().products().searchTerms());

        if (locationIds.isEmpty() || searchTerms.isEmpty()) {
            log.warn("Scheduler: skipping Kroger products run — locationIds or searchTerms not configured.");
            return;
        }

        log.info("Scheduler: starting Kroger products ingestion — locationCount={}, termCount={}",
                locationIds.size(), searchTerms.size());
        try {
            String summary = productIngestion.ingest(locationIds, searchTerms, LOCKED_BY);
            log.info("Scheduler: Kroger products ingestion complete — {}", summary);
        } catch (IngestionLockException e) {
            log.info("Scheduler: Kroger products run skipped — lock already held: {}", e.getMessage());
        } catch (Exception e) {
            try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
                log.error("Scheduler: Kroger products run failed unexpectedly: {}", e.getMessage(), e);
            }
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}