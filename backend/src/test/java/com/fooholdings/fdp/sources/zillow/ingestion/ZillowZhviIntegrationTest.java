package com.fooholdings.fdp.sources.zillow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
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
class ZillowZhviIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp")
            .withUsername("fdp")
            .withPassword("fdp");

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("fdp.geo.seeding.enabled", () -> false);
        registry.add("fdp.scheduler.zillow.enabled", () -> false);
    }

    @Autowired
    ZillowZhviAdapter adapter;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rerunningSameZhviIngestIsIdempotent() {
        int first = adapter.ingest();
        Integer countAfterFirst = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'ZillowZhviAdapter'",
                Integer.class
        );

        int second = adapter.ingest();
        Integer countAfterSecond = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'ZillowZhviAdapter'",
                Integer.class
        );

        assertThat(first).isGreaterThan(0);
        assertThat(second).isGreaterThan(0);
        assertThat(countAfterSecond).isEqualTo(countAfterFirst);
    }
}