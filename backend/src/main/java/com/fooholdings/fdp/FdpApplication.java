package com.fooholdings.fdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fooholdings.fdp.admin.security.AdminSecurityProperties;
import com.fooholdings.fdp.api.service.Where2MoveStaplesProperties;
import com.fooholdings.fdp.core.scheduler.FdpSchedulerProperties;
import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;
import com.fooholdings.fdp.geo.config.GeoProperties;

/**
 * Entry point for Foo Data Platform.
 *
 * Add future source properties classes here as they are created.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        KrogerProperties.class,
        Where2MoveStaplesProperties.class,
        FdpSchedulerProperties.class,
        AdminSecurityProperties.class,
        com.fooholdings.fdp.admin.security.AdminSecurityProperties.class,
        com.fooholdings.fdp.admin.ingestion.FdpQuotaProperties.class,
        GeoProperties.class
})
public class FdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FdpApplication.class, args);
    }
}
