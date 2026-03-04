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
 * Example YAML:
 * <pre>
 * fdp:
 *   scheduler:
 *     kroger:
 *       enabled: false          # off until admin dashboard is ready
 *       locations:
 *         cron: "0 0 2 * * *"  # 2am daily
 *         zipCodes:
 *           - "77002"
 *       products:
 *         cron: "0 30 2 * * *" # 2:30am daily
 *         locationIds:
 *           - "01400301"
 *         searchTerms:
 *           - "milk"
 *           - "eggs"
 * </pre>
 */
@ConfigurationProperties(prefix = "fdp.scheduler")
public record FdpSchedulerProperties(Kroger kroger) {

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
}
