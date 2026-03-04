package com.fooholdings.fdp.admin.jobs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
public class AdminJobExecutorConfig {

    @Bean(name = "adminJobTaskExecutor")
    public TaskExecutor adminJobTaskExecutor() {
        SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor("admin-job-");
        exec.setVirtualThreads(true);
        return exec;
    }
}