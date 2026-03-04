package com.fooholdings.fdp.sources.kroger.scheduler;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.core.ingestion.IngestionLockException;
import com.fooholdings.fdp.core.logging.ErrorCategory;
import com.fooholdings.fdp.core.logging.ErrorCategoryMdc;
import com.fooholdings.fdp.core.scheduler.FdpSchedulerProperties;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerLocationIngestionService;
import com.fooholdings.fdp.sources.kroger.ingestion.KrogerProductIngestionService;

/**
 * Scheduled ingestion jobs for the Kroger source.
 *
 * The bean always exists so a future admin dashboard can inspect and toggle
 * it without an app restart. The enabled flag is checked at runtime inside
 * each method rather than via @ConditionalOnProperty (which would require
 * a restart to re-enable).
 *
 * Both jobs use lockedBy="scheduler" so ingestion_run records are clearly
 * distinguishable from manual trigger runs (lockedBy="fdp-backend").
 *
 * Lock contention is treated as INFO, not an error — it means a manually
 * triggered run or a previous scheduled run is still in progress, which is
 * expected behaviour.
 */
@Component
public class KrogerIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(KrogerIngestionScheduler.class);
    private static final String LOCKED_BY = "scheduler";

    private final FdpSchedulerProperties props;
    private final KrogerLocationIngestionService locationIngestion;
    private final KrogerProductIngestionService productIngestion;

    public KrogerIngestionScheduler(FdpSchedulerProperties props,
                                    KrogerLocationIngestionService locationIngestion,
                                    KrogerProductIngestionService productIngestion) {
        this.props = props;
        this.locationIngestion = locationIngestion;
        this.productIngestion = productIngestion;
    }

    /**
     * Scheduled location ingestion. Skips silently when:
     *  - fdp.scheduler.kroger.enabled is false
     *  - zipCodes list is empty or not configured
     *  - another ingestion run already holds the lock
     */
    @Scheduled(cron = "${fdp.scheduler.kroger.locations.cron}")
    public void runLocations() {
        if (!props.kroger().enabled()) {
            log.debug("Kroger location scheduler is disabled — skipping run.");
            return;
        }

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
            // Lock contention is expected, not an error — another run is in progress.
            log.info("Scheduler: Kroger locations run skipped — lock already held: {}", e.getMessage());
        } catch (Exception e) {
            // Ingestion services already log and record failure; just capture category here.
            try (@SuppressWarnings("unused") var cat = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
                log.error("Scheduler: Kroger locations run failed unexpectedly: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Scheduled product ingestion. Skips silently when:
     *  - fdp.scheduler.kroger.enabled is false
     *  - locationIds or searchTerms are empty or not configured
     *  - another ingestion run already holds the lock
     */
    @Scheduled(cron = "${fdp.scheduler.kroger.products.cron}")
    public void runProducts() {
        if (!props.kroger().enabled()) {
            log.debug("Kroger product scheduler is disabled — skipping run.");
            return;
        }

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
