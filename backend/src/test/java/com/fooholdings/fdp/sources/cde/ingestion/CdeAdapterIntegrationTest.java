package com.fooholdings.fdp.sources.cde.ingestion;

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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@Testcontainers
@SpringBootTest
@WireMockTest
class CdeAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    private static int wireMockPort;

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",         postgres::getJdbcUrl);
        registry.add("spring.datasource.username",    postgres::getUsername);
        registry.add("spring.datasource.password",    postgres::getPassword);
        registry.add("spring.flyway.enabled",         () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("fdp.geo.seeding.enabled",       () -> false);
        registry.add("fdp.scheduler.crime.enabled",   () -> false);
        registry.add("fdp.scheduler.fred.enabled",    () -> false);
        registry.add("cde.base-url",                  () -> "http://localhost:" + wireMockPort);
        registry.add("cde.offenses-known-path",       () -> "/test/offenses");
        registry.add("cde.clearances-path",           () -> "/test/clearances");
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void stubWireMock(WireMockRuntimeInfo wmInfo) {
        wireMockPort = wmInfo.getHttpPort();
        // Minimal CSV with a national row and one state row
        String csv = """
            year,state,violent_crime_rate,property_crime_rate,actual,per_100000
            2024,United States,380.7,1954.4,6900000,2335.1
            2024,Texas,446.5,2456.7,720000,2903.2
            """;

        stubFor(get(urlPathEqualTo("/test/offenses"))
                .willReturn(ok(csv).withHeader("Content-Type", "text/csv")));
        stubFor(get(urlPathEqualTo("/test/clearances"))
                .willReturn(ok("""
                        year,state,actual,per_100000
                        2023,United States,NULL,NULL
                        """)
                        .withHeader("Content-Type", "text/csv")));
    }

    @Autowired CdeAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void cdeWritesOnlyNationalAndStateRows() {
        adapter.ingest();

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
        adapter.ingest();
        Integer countFirst = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'CDE'", Integer.class);

        adapter.ingest();
        Integer countSecond = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'CDE'", Integer.class);

        assertThat(countSecond).isEqualTo(countFirst);
    }

    @Test
    void cdeProducesExpectedCategories() {
        adapter.ingest();

        // All categories must be within the charter-defined set
        var unexpectedCategories = jdbc.queryForList(
                """
                select distinct category
                from fdp_geo.area_snapshot
                where source = 'CDE'
                  and category not in (
                    'safety.crime.violent_rate',
                    'safety.crime.property_rate',
                    'safety.crime.total_incidents_per_100k'
                  )
                """,
                String.class
        );
        assertThat(unexpectedCategories).isEmpty();
    }
}
