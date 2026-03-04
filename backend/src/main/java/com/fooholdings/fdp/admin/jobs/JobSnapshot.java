package com.fooholdings.fdp.admin.jobs;

import java.time.Instant;

public record JobSnapshot(
        JobRunStatus status,
        Instant lastStartedAt,
        Instant lastFinishedAt,
        String lastMessage
) {
    public static JobSnapshot never() {
        return new JobSnapshot(JobRunStatus.NEVER, null, null, null);
    }
}