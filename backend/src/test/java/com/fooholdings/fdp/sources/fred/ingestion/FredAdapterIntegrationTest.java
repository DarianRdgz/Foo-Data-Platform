package com.fooholdings.fdp.sources.fred.ingestion;

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
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@Testcontainers
@SpringBootTest
@WireMockTest
class FredAdapterIntegrationTest {

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
        registry.add("fdp.scheduler.fred.enabled",    () -> false);
        registry.add("fdp.scheduler.crime.enabled",   () -> false);
        registry.add("fred.api-key",                  () -> "test-key");
        registry.add("fred.base-url",                 () -> "http://localhost:" + wireMockPort);
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void stubFredApi(WireMockRuntimeInfo wmInfo) {
        wireMockPort = wmInfo.getHttpPort();

        stubFor(get(urlPathEqualTo("/fred/series"))
                .willReturn(okJson("""
                    {"seriess":[{
                      "id":"UNRATE","title":"Unemployment Rate",
                      "units":"Percent","frequency":"Monthly",
                      "seasonal_adjustment":"Seasonally Adjusted","last_updated":"2026-02-07"
                    }]}
                    """)));

        stubFor(get(urlPathEqualTo("/fred/series/observations"))
                .willReturn(okJson("""
                    {"observations":[
                      {"date":"2025-12-01","value":"4.1","realtime_start":"2026-01-01","realtime_end":"2026-01-31"},
                      {"date":"2025-11-01","value":"4.2","realtime_start":"2025-12-01","realtime_end":"2025-12-31"},
                      {"date":"2025-10-01","value":".","realtime_start":"2025-11-01","realtime_end":"2025-11-30"}
                    ]}
                    """)));
    }

    @Autowired FredAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void fredIngestWritesRowsExcludingDotValues() {
        int written = adapter.ingest();
        assertThat(written).isGreaterThan(0);

        // The "." observation must NOT have been written
        Integer dotRows = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FRED' " +
                "and snapshot_period = '2025-10-01'",
                Integer.class
        );
        assertThat(dotRows).isZero();
    }

    @Test
    void fredIngestIsIdempotent() {
        adapter.ingest();
        Integer countFirst = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FRED'", Integer.class);

        adapter.ingest();
        Integer countSecond = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FRED'", Integer.class);

        assertThat(countSecond).isEqualTo(countFirst);
    }

    @Test
    void fredPayloadContainsRequiredFields() {
        adapter.ingest();
        String payload = jdbc.queryForObject(
                "select payload::text from fdp_geo.area_snapshot where source = 'FRED' limit 1",
                String.class
        );
        assertThat(payload)
                .contains("\"units\"")
                .contains("\"frequency\"")
                .contains("\"vintageDate\"")
                .contains("\"seriesId\"");
    }
}