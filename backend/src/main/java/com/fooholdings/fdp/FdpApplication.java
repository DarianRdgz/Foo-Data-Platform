package com.fooholdings.fdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.fooholdings.fdp.api.service.Where2MoveStaplesProperties;
import com.fooholdings.fdp.sources.kroger.config.KrogerProperties;

/**
 * Entry point for Foo Data Platform.
 *
 * Add future source properties classes here as they are created.
 */
@SpringBootApplication
@EnableConfigurationProperties({KrogerProperties.class, Where2MoveStaplesProperties.class})
public class FdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(FdpApplication.class, args);
    }
}
