package com.fooholdings.fdp.geo.seed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.fooholdings.fdp.geo.config.GeoLevelGate;
import com.fooholdings.fdp.geo.config.GeoProperties;

@Component
public class GeoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GeoSeedRunner.class);

    private final GeoProperties geoProperties;
    private final GeoSeedService seedService;
    private final GeoLevelGate geoLevelGate;

    public GeoSeedRunner(
            GeoProperties geoProperties,
            GeoSeedService seedService,
            GeoLevelGate geoLevelGate
    ) {
        this.geoProperties = geoProperties;
        this.seedService = seedService;
        this.geoLevelGate = geoLevelGate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (geoProperties.seeding() == null || !geoProperties.seeding().enabled()) {
            return;
        }

        String seedDir = geoProperties.seeding().seedDir();
        log.warn("fdp.geo.seeding.enabled=true — bootstrapping geo_areas from seedDir={}", seedDir);

        if (geoLevelGate.isEnabled("metro")) {
            seedService.seedMetrosFromSeedDir(seedDir);
        } else {
            log.info("Skipping metro seeding because geo level is disabled");
        }

        if (geoLevelGate.isEnabled("county")) {
            seedService.seedCountiesFromSeedDir(seedDir);
        } else {
            log.info("Skipping county seeding because geo level is disabled");
        }

        if (geoLevelGate.isEnabled("zip")) {
            seedService.seedZipsFromSeedDir(seedDir);
        } else {
            log.info("Skipping zip seeding because geo level is disabled");
        }

        log.warn("Geo seeding completed.");
    }
}