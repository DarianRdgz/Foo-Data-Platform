package com.fooholdings.fdp.sources.noaa.scheduler;

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
import com.fooholdings.fdp.sources.noaa.ingestion.NoaaStormIngestionService;

import jakarta.annotation.PostConstruct;

@Component
public class NoaaStormScheduler {

    private static final Logger log = LoggerFactory.getLogger(NoaaStormScheduler.class);
    private static final String LOCKED_BY = "scheduler";
    private static final String JOB_NAME  = "noaa-storm-events";

    private final JobRegistry             jobRegistry;
    private final FdpSchedulerProperties  props;
    private final NoaaStormIngestionService ingestionService;

    public NoaaStormScheduler(JobRegistry jobRegistry,
                              FdpSchedulerProperties props,
                              NoaaStormIngestionService ingestionService) {
        this.jobRegistry      = jobRegistry;
        this.props            = props;
        this.ingestionService = ingestionService;
    }

    @PostConstruct
    @SuppressWarnings("unused")
    void registerJobs() {
        jobRegistry.register(new JobDefinition(
                JOB_NAME,
                "NOAA",
                () -> props.disaster().noaa().cron(),
                () -> props.disaster().enabled()
        ), this::runInternal);
    }

    @Scheduled(cron = "${fdp.scheduler.disaster.noaa.cron}")
    public void run() {
        jobRegistry.runScheduled(JOB_NAME);
    }

    private void runInternal() {
        try {
            log.info("Scheduler: starting NOAA storm events ingestion");
            log.info("Scheduler: {}", ingestionService.ingest(LOCKED_BY));
        } catch (IngestionLockException e) {
            log.info("Scheduler: NOAA skipped — lock held: {}", e.getMessage());
        } catch (Exception e) {
            try (ErrorCategoryMdc mdc = ErrorCategoryMdc.with(ErrorCategory.UNCLASSIFIED)) {
                log.error("Scheduler: NOAA failed: {}", e.getMessage(), e);
            }
            throw e;
        }
    }
}