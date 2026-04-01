package com.fooholdings.fdp.sources.fema.ingestion;

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
class FemaDisasterAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    private static int wireMockPort;

    // Recent cutoff year for fixture data
    private static final int RECENT_YEAR = java.time.Year.now().getValue() - 2;

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",           postgres::getJdbcUrl);
        r.add("spring.datasource.username",      postgres::getUsername);
        r.add("spring.datasource.password",      postgres::getPassword);
        r.add("spring.flyway.enabled",           () -> true);
        r.add("spring.jpa.hibernate.ddl-auto",   () -> "validate");
        r.add("fdp.geo.seeding.enabled",         () -> false);
        r.add("fdp.scheduler.disaster.enabled",  () -> false);
        r.add("fdp.scheduler.education.enabled", () -> false);
        r.add("fema.disaster-declarations-url",  () -> "http://localhost:" + wireMockPort + "/fema.csv");
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void stubFema(WireMockRuntimeInfo wm) {
        wireMockPort = wm.getHttpPort();

        // Two statewide (county 000) + one county-specific declaration
        String csv = String.format("""
                fipsStateCode,fipsCountyCode,incidentType,declarationDate
                48,000,Severe Storm(s),%d-03-15T00:00:00.000Z
                48,113,Flood,%d-06-22T00:00:00.000Z
                06,000,Wildfire,%d-09-01T00:00:00.000Z
                """, RECENT_YEAR, RECENT_YEAR, RECENT_YEAR);

        stubFor(get(urlPathEqualTo("/fema.csv"))
                .willReturn(ok(csv).withHeader("Content-Type", "text/csv")));
    }

    @Autowired FemaDisasterAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesStateAndCountyRowsOnly() {
        adapter.ingest();

        Integer nonStateCounty = jdbc.queryForObject(
                """
                select count(*)
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'FEMA'
                  and g.geo_level not in ('state', 'county')
                """,
                Integer.class);
        assertThat(nonStateCounty).isZero();
    }

    @Test
    void stateAggregationCountsAllDeclarations() {
        adapter.ingest();

        // Texas (48) has 2 declarations in fixture
        Integer txCount = jdbc.queryForObject(
                """
                select (payload->>'disasterCountLast10yr')::int
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'FEMA'
                  and g.geo_level = 'state'
                  and g.fips_code = '48'
                """,
                Integer.class);
        assertThat(txCount).isEqualTo(2);
    }

    @Test
    void declarationsPer100kIsNull() {
        adapter.ingest();

        Integer withNullPer100k = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot " +
                "where source = 'FEMA' and payload->>'declarationsPer100k' is null",
                Integer.class);
        Integer total = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FEMA'",
                Integer.class);
        assertThat(withNullPer100k).isEqualTo(total);
    }

    @Test
    void ingestIsIdempotent() {
        adapter.ingest();
        Integer first = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FEMA'", Integer.class);
        adapter.ingest();
        Integer second = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'FEMA'", Integer.class);
        assertThat(second).isEqualTo(first);
    }
}