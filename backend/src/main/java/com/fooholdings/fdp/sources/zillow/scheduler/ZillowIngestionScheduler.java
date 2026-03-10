package com.fooholdings.fdp.sources.zillow.scheduler;

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
import com.fooholdings.fdp.sources.zillow.ingestion.ZillowAffordabilityIngestionService;
import com.fooholdings.fdp.sources.zillow.ingestion.ZillowListingsIngestionService;
import com.fooholdings.fdp.sources.zillow.ingestion.ZillowZhviIngestionService;
import com.fooholdings.fdp.sources.zillow.ingestion.ZillowZoriIngestionService;

import jakarta.annotation.PostConstruct;

@Component
public class ZillowIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZillowIngestionScheduler.class);

    private static final String LOCKED_BY = "scheduler";
    private static final String JOB_ZHVI = "zillow-zhvi";
    private static final String JOB_ZORI = "zillow-zori";
    private static final String JOB_LISTINGS = "zillow-listings";
    private static final String JOB_AFFORDABILITY = "zillow-affordability";

    private final JobRegistry jobRegistry;
    private final FdpSchedulerProperties props;
    private final ZillowZhviIngestionService zhvi;
    private final ZillowZoriIngestionService zori;
    private final ZillowListingsIngestionService listings;
    private final ZillowAffordabilityIngestionService affordability;

    public ZillowIngestionScheduler(
            JobRegistry jobRegistry,
            FdpSchedulerProperties props,
            ZillowZhviIngestionService zhvi,
            ZillowZoriIngestionService zori,
            ZillowListingsIngestionService listings,
            ZillowAffordabilityIngestionService affordability
    ) {
        this.jobRegistry = jobRegistry;
        this.props = props;
        this.zhvi = zhvi;
        this.zori = zori;
        this.listings = listings;
        this.affordability = affordability;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerJobs() {
        jobRegistry.register(new JobDefinition(
                JOB_ZHVI, "ZILLOW", () -> props.zillow().zhvi().cron(), () -> props.zillow().enabled()
        ), this::runZhviInternal);

        jobRegistry.register(new JobDefinition(
                JOB_ZORI, "ZILLOW", () -> props.zillow().zori().cron(), () -> props.zillow().enabled()
        ), this::runZoriInternal);

        jobRegistry.register(new JobDefinition(
                JOB_LISTINGS, "ZILLOW", () -> props.zillow().listings().cron(), () -> props.zillow().enabled()
        ), this::runListingsInternal);

        jobRegistry.register(new JobDefinition(
                JOB_AFFORDABILITY, "ZILLOW", () -> props.zillow().affordability().cron(), () -> props.zillow().enabled()
        ), this::runAffordabilityInternal);
    }

    @Scheduled(cron = "${fdp.scheduler.zillow.zhvi.cron}")
    public void runZhvi() {
        jobRegistry.runScheduled(JOB_ZHVI);
    }

    @Scheduled(cron = "${fdp.scheduler.zillow.zori.cron}")
    public void runZori() {
        jobRegistry.runScheduled(JOB_ZORI);
    }

    @Scheduled(cron = "${fdp.scheduler.zillow.listings.cron}")
    public void runListings() {
        jobRegistry.runScheduled(JOB_LISTINGS);
    }

    @Scheduled(cron = "${fdp.scheduler.zillow.affordability.cron}")
    public void runAffordability() {
        jobRegistry.runScheduled(JOB_AFFORDABILITY);
    }

    private void runZhviInternal() {
        try {
            log.info("Scheduler: starting Zillow ZHVI ingestion");
            log.info("Scheduler: {}", zhvi.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: Zillow ZHVI skipped due to lock: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc) {
                log.error("Scheduler: Zillow ZHVI failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }

    private void runZoriInternal() {
        try {
            log.info("Scheduler: starting Zillow ZORI ingestion");
            log.info("Scheduler: {}", zori.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: Zillow ZORI skipped due to lock: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc){
                log.error("Scheduler: Zillow ZORI failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }

    private void runListingsInternal() {
        try {
            log.info("Scheduler: starting Zillow listings ingestion");
            log.info("Scheduler: {}", listings.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: Zillow listings skipped due to lock: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc) {
                log.error("Scheduler: Zillow listings failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }

    private void runAffordabilityInternal() {
        try {
            log.info("Scheduler: starting Zillow affordability ingestion");
            log.info("Scheduler: {}", affordability.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: Zillow affordability skipped due to lock: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc) {
                log.error("Scheduler: Zillow affordability failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }
}