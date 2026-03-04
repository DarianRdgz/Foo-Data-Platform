package com.fooholdings.fdp.admin.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobRegistry {

    private record Entry(JobDefinition def, Runnable task) {}

    private final Map<String, Entry> jobs = new ConcurrentHashMap<>();
    private final Map<String, Boolean> runtimeEnabled = new ConcurrentHashMap<>();
    private final Map<String, JobSnapshot> snapshots = new ConcurrentHashMap<>();

    private final TaskExecutor taskExecutor;

    public JobRegistry(@Qualifier("adminJobTaskExecutor") TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public void register(JobDefinition def, Runnable task) {
        jobs.put(def.name(), new Entry(def, task));
        runtimeEnabled.putIfAbsent(def.name(), Boolean.TRUE.equals(def.enabledSupplier().get()));
        snapshots.putIfAbsent(def.name(), JobSnapshot.never());
    }

    public List<JobView> list() {
        List<JobView> out = new ArrayList<>();
        for (var kv : jobs.entrySet()) {
            String name = kv.getKey();
            Entry entry = kv.getValue();

            boolean enabled = isEnabled(name);
            String cron = safe(entry.def.cronExpressionSupplier().get());
            JobSnapshot snap = snapshots.getOrDefault(name, JobSnapshot.never());

            out.add(new JobView(
                    name,
                    safe(entry.def.source()),
                    cron,
                    enabled,
                    snap.status(),
                    snap.lastStartedAt(),
                    snap.lastFinishedAt()
            ));
        }
        out.sort(Comparator.comparing(JobView::source).thenComparing(JobView::name));
        return out;
    }

    public boolean isEnabled(String jobName) {
        Boolean runtime = runtimeEnabled.get(jobName);
        return runtime != null && runtime;
    }

    public void enable(String jobName) { ensureExists(jobName); runtimeEnabled.put(jobName, true); }
    public void disable(String jobName) { ensureExists(jobName); runtimeEnabled.put(jobName, false); }

    /** Scheduled runs respect enabled flag and never throw outward. */
    public void runScheduled(String jobName) {
        Entry entry = ensureExists(jobName);
        if (!isEnabled(jobName)) return;
        runInternal(jobName, entry.task, false);
    }

    /** Trigger-now bypasses enabled flag and runs async. */
    public void triggerAsync(String jobName) {
        Entry entry = ensureExists(jobName);
        taskExecutor.execute(() -> runInternal(jobName, entry.task, true));
    }

    private void runInternal(String jobName, Runnable task, boolean swallowAll) {
        JobSnapshot current = snapshots.getOrDefault(jobName, JobSnapshot.never());
        if (current.status() == JobRunStatus.RUNNING) return;

        Instant started = Instant.now();
        snapshots.put(jobName, new JobSnapshot(JobRunStatus.RUNNING, started, current.lastFinishedAt(), null));

        try {
            task.run();
            Instant finished = Instant.now();
            snapshots.put(jobName, new JobSnapshot(JobRunStatus.SUCCESS, started, finished, null));
        } catch (Exception e) {
            Instant finished = Instant.now();
            snapshots.put(jobName, new JobSnapshot(JobRunStatus.FAILED, started, finished, safe(e.getMessage())));

            // For scheduled runs we typically swallow; for trigger threads we also swallow to avoid noisy executor output.
            if (!swallowAll) {
                // still swallow to keep behavior stable (do nothing)
            }
        }
    }

    private Entry ensureExists(String jobName) {
        Entry entry = jobs.get(jobName);
        if (entry == null) throw new IllegalArgumentException("Unknown job: " + jobName);
        return entry;
    }

    private static String safe(String s) { return s == null ? "" : s; }
}