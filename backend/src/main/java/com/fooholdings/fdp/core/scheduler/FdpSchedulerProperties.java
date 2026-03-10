package com.fooholdings.fdp.core.scheduler;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Scheduler configuration properties bound from application.yml under fdp.scheduler.
 *
 * enabled flag is checked at runtime inside each scheduled method so that:
 *  - The scheduler bean always exists (admin dashboard can inspect / toggle it)
 *  - No app restart is needed to enable/disable (future dashboard support)
 *
 */
@ConfigurationProperties(prefix = "fdp.scheduler")
public record FdpSchedulerProperties(Kroger kroger, Zillow zillow) {

    public record Kroger(
            boolean enabled,
            Locations locations,
            Products products
    ) {}

    public record Locations(
            String cron,
            List<String> zipCodes
    ) {}

    public record Products(
            String cron,
            List<String> locationIds,
            List<String> searchTerms
    ) {}

    public record Zillow(
            boolean enabled,
            Job zhvi,
            Job zori,
            Job listings,
            Job affordability
    ) {}

    public record Job(String cron) {}
}
