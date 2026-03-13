package com.fooholdings.fdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fooholdings.fdp.admin.security.AdminSecurityProperties;
import com.fooholdings.fdp.api.service.Where2MoveStaplesProperties;
import com.fooholdings.fdp.core.scheduler.FdpSchedulerProperties;
import com.fooholdings.fdp.geo.config.GeoProperties;
import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;
import com.fooholdings.fdp.sources.zillow.config.ZillowProperties;
import com.fooholdings.fdp.sources.cde.config.CdeProperties;
import com.fooholdings.fdp.sources.fred.config.FredProperties;
import com.fooholdings.fdp.sources.collegescorecard.config.CollegeScorecardProperties;
import com.fooholdings.fdp.sources.fema.config.FemaProperties;
import com.fooholdings.fdp.sources.noaa.config.NoaaProperties;
import com.fooholdings.fdp.admin.ingestion.FdpQuotaProperties;

/**
 * Entry point for Foo Data Platform.
 *
 * Add future source properties classes here as they are created.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableConfigurationProperties({
        KrogerProperties.class,
        Where2MoveStaplesProperties.class,
        FdpSchedulerProperties.class,
        AdminSecurityProperties.class,
        FdpQuotaProperties.class,
        GeoProperties.class,
        ZillowProperties.class,
        CdeProperties.class,
        FredProperties.class,
        CollegeScorecardProperties.class,  
        FemaProperties.class,               
        NoaaProperties.class 
})
public class FdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FdpApplication.class, args);
    }
}
