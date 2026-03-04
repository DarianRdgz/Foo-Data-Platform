package com.fooholdings.fdp.admin.jobs;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobsController {

    private final JobRegistry registry;

    public AdminJobsController(JobRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<JobView> listJobs() {
        return registry.list();
    }

    @PostMapping("/{jobName}/enable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enable(@PathVariable String jobName) {
        registry.enable(jobName);
    }

    @PostMapping("/{jobName}/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@PathVariable String jobName) {
        registry.disable(jobName);
    }

    @PostMapping("/{jobName}/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void trigger(@PathVariable String jobName) {
        registry.triggerAsync(jobName);
    }
}