package com.fooholdings.fdp.sources.cde.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class CdeAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    private static final Path downloadDir = initTempDir();

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("fdp.geo.seeding.enabled", () -> false);
        registry.add("fdp.scheduler.crime.enabled", () -> false);
        registry.add("fdp.scheduler.fred.enabled", () -> false);
        registry.add("cde.mode", () -> "LOCAL");
        registry.add("cde.download-dir", () -> downloadDir.toString());
        registry.add("cde.work-dir", () -> downloadDir.resolve("work").toString());
    }

    @Autowired
    CdeIngestionService ingestionService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() throws Exception {
        jdbc.update("delete from fdp_geo.area_snapshot where source = 'CDE'");
        jdbc.update("delete from fdp_core.source_artifact");

        clearDirectory(downloadDir);
        Files.createDirectories(downloadDir);

        Files.writeString(
                downloadDir.resolve("offenses_known_to_law_enforcement__2024__us.csv"),
                """
                year,state,violent_crime_rate,property_crime_rate,actual,per_100000
                2024,United States,380.7,1954.4,6900000,2335.1
                2024,Texas,446.5,2456.7,720000,2903.2
                """
        );
    }

    @Test
    void cdeWritesOnlyNationalAndStateRows() {
        ingestionService.ingest("test");

        Integer nonNationalState = jdbc.queryForObject(
                """
                select count(*)
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'CDE'
                  and g.geo_level not in ('national', 'state')
                """,
                Integer.class
        );
        assertThat(nonNationalState).isZero();
    }

    @Test
    void cdeIngestIsIdempotent() {
        ingestionService.ingest("test");
        Integer countFirst = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'CDE'",
                Integer.class
        );

        ingestionService.ingest("test");
        Integer countSecond = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'CDE'",
                Integer.class
        );

        assertThat(countSecond).isEqualTo(countFirst);
    }

    @Test
    void cdeArtifactsAreMarkedIngested() {
        ingestionService.ingest("test");

        var statuses = jdbc.queryForList(
                "select status from fdp_core.source_artifact order by original_filename",
                String.class
        );

        assertThat(statuses).containsOnly("INGESTED");
    }

    private static Path initTempDir() {
        try {
            return Files.createTempDirectory("cde-artifacts-test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }

        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a))
                .filter(path -> !path.equals(dir))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
    }
}
