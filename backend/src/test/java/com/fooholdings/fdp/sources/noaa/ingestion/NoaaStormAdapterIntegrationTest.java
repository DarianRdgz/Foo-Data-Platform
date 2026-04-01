package com.fooholdings.fdp.sources.noaa.ingestion;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

@Testcontainers
@SpringBootTest
@WireMockTest
class NoaaStormAdapterIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    private static int wireMockPort;
    private static final int YEAR = java.time.Year.now().getValue();

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
        r.add("noaa.storm-events-base-url",      () -> "http://localhost:" + wireMockPort + "/noaa");
    }

    @BeforeEach
    @SuppressWarnings("unused")
    void stubNoaa(WireMockRuntimeInfo wm) throws Exception {
        wireMockPort = wm.getHttpPort();

        String filename = "StormEvents_details-ftp_v1.0_d" + YEAR + "_c" + YEAR + "0115.csv.gz";

        // Directory index listing
        stubFor(get(urlPathEqualTo("/noaa/"))
                .willReturn(ok("<html><a href=\"" + filename + "\">" + filename + "</a></html>")));

        // Previous year — empty (to simplify fixture)
        String prevFilename = "StormEvents_details-ftp_v1.0_d" + (YEAR - 1) + "_c" + YEAR + "0115.csv.gz";
        stubFor(get(urlPathEqualTo("/noaa/"))
                .willReturn(ok("<html><a href=\"" + filename + "\">" + filename + "</a>" +
                               "<a href=\"" + prevFilename + "\">" + prevFilename + "</a></html>")));

        // Gzipped CSV
        String csv = "STATE_FIPS,CZ_FIPS,CZ_TYPE,CZ_NAME,DAMAGE_PROPERTY,DAMAGE_CROPS,DEATHS_DIRECT,DEATHS_INDIRECT\n" +
                     "48,113,C,DALLAS,1.5M,0,2,0\n" +
                     "48,201,C,HARRIS,500K,0,0,1\n" +
                     "06,037,C,LOS ANGELES,2M,0,0,0\n";

        byte[] gzipped = gzip(csv.getBytes());
        stubFor(get(urlPathEqualTo("/noaa/" + filename))
                .willReturn(aResponse().withBody(gzipped).withHeader("Content-Type", "application/gzip")));
        stubFor(get(urlPathEqualTo("/noaa/" + prevFilename))
                .willReturn(aResponse().withBody(gzip("STATE_FIPS,CZ_FIPS,CZ_TYPE,CZ_NAME,DAMAGE_PROPERTY,DAMAGE_CROPS,DEATHS_DIRECT,DEATHS_INDIRECT\n".getBytes()))
                        .withHeader("Content-Type", "application/gzip")));
    }

    @Autowired NoaaStormAdapter adapter;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writesStateAndCountyRowsOnly() {
        adapter.ingest();

        Integer nonStateCounty = jdbc.queryForObject(
                """
                select count(*)
                from fdp_geo.area_snapshot s
                join fdp_geo.geo_areas g on g.geo_id = s.geo_id
                where s.source = 'NOAA'
                  and g.geo_level not in ('state', 'county')
                """,
                Integer.class);
        assertThat(nonStateCounty).isZero();
    }

    @Test
    void parsesAbbreviatedDamageValues() {
        // Unit test for damage parsing — no Spring context needed
        assertThat(com.fooholdings.fdp.sources.noaa.client.NoaaStormEventsClient.parseDamage("1.5M"))
                .isEqualTo(1_500_000.0);
        assertThat(com.fooholdings.fdp.sources.noaa.client.NoaaStormEventsClient.parseDamage("500K"))
                .isEqualTo(500_000.0);
        assertThat(com.fooholdings.fdp.sources.noaa.client.NoaaStormEventsClient.parseDamage("2B"))
                .isEqualTo(2_000_000_000.0);
        assertThat(com.fooholdings.fdp.sources.noaa.client.NoaaStormEventsClient.parseDamage(null))
                .isEqualTo(0.0);
    }

    @Test
    void ingestIsIdempotent() {
        adapter.ingest();
        Integer first = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'NOAA'", Integer.class);
        adapter.ingest();
        Integer second = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'NOAA'", Integer.class);
        assertThat(second).isEqualTo(first);
    }

    private static byte[] gzip(byte[] data) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(data);
        }
        return bos.toByteArray();
    }
}