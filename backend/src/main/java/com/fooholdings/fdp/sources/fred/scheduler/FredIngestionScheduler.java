package com.fooholdings.fdp.sources.fred.scheduler;

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
import com.fooholdings.fdp.sources.fred.ingestion.FredIngestionService;

import jakarta.annotation.PostConstruct;

@Component
public class FredIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FredIngestionScheduler.class);
    private static final String LOCKED_BY = "scheduler";
    private static final String JOB_NAME  = "fred-series";

    private final JobRegistry            jobRegistry;
    private final FdpSchedulerProperties props;
    private final FredIngestionService   ingestionService;

    public FredIngestionScheduler(JobRegistry jobRegistry,
                                   FdpSchedulerProperties props,
                                   FredIngestionService ingestionService) {
        this.jobRegistry      = jobRegistry;
        this.props            = props;
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerJobs() {
        jobRegistry.register(new JobDefinition(
                JOB_NAME,
                "FRED",
                () -> props.fred().series().cron(),
                () -> props.fred().enabled()
        ), this::runInternal);
    }

    @Scheduled(cron = "${fdp.scheduler.fred.series.cron}")
    public void run() {
        jobRegistry.runScheduled(JOB_NAME);
    }

    private void runInternal() {
        try {
            log.info("Scheduler: starting FRED economic series ingestion");
            log.info("Scheduler: {}", ingestionService.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: FRED skipped — lock held: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc){
                log.error("Scheduler: FRED failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }
}