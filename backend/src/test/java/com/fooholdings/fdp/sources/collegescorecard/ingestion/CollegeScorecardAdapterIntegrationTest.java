package com.fooholdings.fdp.sources.collegescorecard.ingestion;

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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@Testcontainers
@SpringBootTest
@WireMockTest
class CollegeScorecardAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    private static int wireMockPort;

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",           postgres::getJdbcUrl);
        r.add("spring.datasource.username",      postgres::getUsername);
        r.add("spring.datasource.password",      postgres::getPassword);
        r.add("spring.flyway.enabled",           () -> true);
        r.add("spring.jpa.hibernate.ddl-auto",   () -> "validate");
        r.add("fdp.geo.seeding.enabled",         () -> false);
        r.add("fdp.scheduler.education.enabled", () -> false);
        r.add("fdp.scheduler.disaster.enabled",  () -> false);
        r.add("college-scorecard.api-key",       () -> "test-key");
        r.add("college-scorecard.base-url",      () -> "http://localhost:" + wireMockPort);
        r.add("college-scorecard.page-size",     () -> "2");
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void stubScorecard(WireMockRuntimeInfo wm) {
        wireMockPort = wm.getHttpPort();

        // Page 0 — 2 results, total=3 so client fetches page 1
        stubFor(get(urlPathEqualTo("/v1/schools.json"))
                .withQueryParam("page", equalTo("0"))
                .willReturn(okJson("""
                    {
                      "metadata": {"total": 3, "page": 0, "per_page": 2},
                      "results": [
                        {
                          "school.name": "Texas A&M",
                          "school.state": "TX",
                          "school.city": "College Station",
                          "latest.admissions.admission_rate.overall": 0.63,
                          "latest.cost.avg_net_price.public": 14200,
                          "latest.earnings.10_yrs_after_entry.median": 52000,
                          "latest.aid.pell_grant_rate": 0.28
                        },
                        {
                          "school.name": "University of Texas",
                          "school.state": "TX",
                          "school.city": "Austin",
                          "latest.admissions.admission_rate.overall": 0.32,
                          "latest.cost.avg_net_price.public": 16800,
                          "latest.earnings.10_yrs_after_entry.median": 60000,
                          "latest.aid.pell_grant_rate": 0.31
                        }
                      ]
                    }
                    """)));

        // Page 1 — last page
        stubFor(get(urlPathEqualTo("/v1/schools.json"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(okJson("""
                    {
                      "metadata": {"total": 3, "page": 1, "per_page": 2},
                      "results": [
                        {
                          "school.name": "Rice University",
                          "school.state": "TX",
                          "school.city": "Houston",
                          "latest.admissions.admission_rate.overall": 0.09,
                          "latest.cost.avg_net_price.private": 28000,
                          "latest.earnings.10_yrs_after_entry.median": 75000,
                          "latest.aid.pell_grant_rate": 0.18
                        }
                      ]
                    }
                    """)));
    }

    @Autowired CollegeScorecardAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void paginatesAndAggregatesStateRows() {
        int written = adapter.ingest();
        assertThat(written).isGreaterThan(0);

        // Texas state rows must exist for each category
        Integer txStateRows = jdbc.queryForObject(
                """
                select count(*)
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'COLLEGE_SCORECARD'
                  and g.geo_level = 'state'
                  and g.fips_code = '48'
                """,
                Integer.class);
        // At minimum: school_count + avg_admission_rate + avg_net_price + avg_earnings + pct_aid
        assertThat(txStateRows).isGreaterThanOrEqualTo(5);
    }

    @Test
    void writesOnlyStateAndCityRows() {
        adapter.ingest();

        Integer nonStateCity = jdbc.queryForObject(
                """
                select count(*)
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'COLLEGE_SCORECARD'
                  and g.geo_level not in ('state', 'city')
                """,
                Integer.class);
        assertThat(nonStateCity).isZero();
    }

    @Test
    void ingestIsIdempotent() {
        adapter.ingest();
        Integer first = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'COLLEGE_SCORECARD'",
                Integer.class);

        adapter.ingest();
        Integer second = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'COLLEGE_SCORECARD'",
                Integer.class);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void payloadContainsFrequencyAnnual() {
        adapter.ingest();
        Integer withFrequency = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot " +
                "where source = 'COLLEGE_SCORECARD' and payload->>'frequency' = 'annual'",
                Integer.class);
        Integer total = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'COLLEGE_SCORECARD'",
                Integer.class);
        assertThat(withFrequency).isEqualTo(total);
    }
}