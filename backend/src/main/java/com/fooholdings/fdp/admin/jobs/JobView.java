package com.fooholdings.fdp.admin.jobs;

import java.time.Instant;

public record JobView(
        String name,
        String source,
        String cron,
        boolean enabled,
        JobRunStatus lastRunStatus,
        Instant lastRunStartedAt,
        Instant lastRunFinishedAt
) { }