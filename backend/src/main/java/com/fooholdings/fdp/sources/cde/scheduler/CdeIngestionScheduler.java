package com.fooholdings.fdp.sources.cde.scheduler;

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
import com.fooholdings.fdp.sources.cde.ingestion.CdeIngestionService;

import jakarta.annotation.PostConstruct;

@Component
public class CdeIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CdeIngestionScheduler.class);
    private static final String LOCKED_BY = "scheduler";
    private static final String JOB_NAME  = "crime-cde";

    private final JobRegistry          jobRegistry;
    private final FdpSchedulerProperties props;
    private final CdeIngestionService  ingestionService;

    public CdeIngestionScheduler(JobRegistry jobRegistry,
                                  FdpSchedulerProperties props,
                                  CdeIngestionService ingestionService) {
        this.jobRegistry      = jobRegistry;
        this.props            = props;
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerJobs() {
        jobRegistry.register(
                new JobDefinition(
                        JOB_NAME,
                        "CDE",
                        () -> props.crime().cde().cron(),
                        () -> props.crime().enabled()
                ),
                this::runInternal
        );
    }

    @Scheduled(cron = "${fdp.scheduler.crime.cde.cron}")
    public void run() {
        jobRegistry.runScheduled(JOB_NAME);
    }

    private void runInternal() {
        try {
            log.info("Scheduler: starting CDE crime ingestion");
            log.info("Scheduler: {}", ingestionService.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: CDE skipped — lock held: {}", e.getMessage());
        } catch (Exception e) {
            ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED);
            try (mdc){
                log.error("Scheduler: CDE failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }
}
