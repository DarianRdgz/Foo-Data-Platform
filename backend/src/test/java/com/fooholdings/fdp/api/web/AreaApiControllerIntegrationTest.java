package com.fooholdings.fdp.api.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AreaApiControllerIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("fdp").withUsername("fdp").withPassword("fdp");

    @DynamicPropertySource
    @SuppressWarnings("unused")
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled", () -> true);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        r.add("fdp.geo.seeding.enabled", () -> false);
        r.add("fdp.scheduler.disaster.enabled", () -> false);
        r.add("fdp.scheduler.education.enabled", () -> false);
        r.add("fdp.scheduler.fred.enabled", () -> false);
        r.add("fdp.scheduler.crime.enabled", () -> false);
        r.add("fdp.public-api.cors.allowed-origins[0]", () -> "https://aboutmyarea.net");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private UUID texasStateId;
    private UUID harrisCountyId;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        jdbc.update("delete from fdp_geo.area_change_log");
        jdbc.update("delete from fdp_geo.area_snapshot where source in ('FRED','ZILLOW','DISASTER_RISK','ROLLUP_TEST')");

        texasStateId = jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' and fips_code = '48' limit 1",
                UUID.class);

        harrisCountyId = jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'county' and fips_code = '48201' limit 1",
                UUID.class);
    }

    @Test
    void areaEndpointReturnsLatestSnapshotsAndBadges() throws Exception {
        insertSnapshot(texasStateId, "housing.home_value", "ZILLOW", LocalDate.of(2024, 1, 1), Map.of("value", 300000));
        insertSnapshot(texasStateId, "housing.home_value", "ZILLOW", LocalDate.of(2025, 1, 1), Map.of("value", 315000));
        insertSnapshot(texasStateId, "economic.unemployment_rate", "FRED", LocalDate.of(2025, 1, 1), Map.of("value", 4.2));
        insertSnapshot(texasStateId, "risk.composite", "DISASTER_RISK", LocalDate.of(2025, 1, 1), Map.of("riskScore", 42.5, "tier", "moderate"));
        insertChangeLog(texasStateId, "housing.home_value", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), 5.0, "up", "significant");

        mvc.perform(get("/api/area/state/{geoId}", "48").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoId").value(texasStateId.toString()))
                .andExpect(jsonPath("$.geoLevel").value("state"))
                .andExpect(jsonPath("$.snapshots").isArray())
                .andExpect(jsonPath("$.snapshots[?(@.category=='housing.home_value')]").exists());

        String body = mvc.perform(get("/api/area/state/{geoId}", "48"))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        List<JsonNode> housingSnapshots = new ArrayList<>();
        for (JsonNode snapshot : root.withArray("snapshots")) {
            if ("housing.home_value".equals(snapshot.path("category").asText())) {
                housingSnapshots.add(snapshot);
            }
        }

        assertThat(housingSnapshots).hasSize(1);
        JsonNode housingSnapshot = housingSnapshots.getFirst();
        assertThat(housingSnapshot.path("change").path("direction").asText()).isEqualTo("up");
    }

    @Test
    void historyEndpointReturnsAscendingPeriodsWithUpperBound() throws Exception {
        for (int year = 2014; year <= 2025; year++) {
            insertSnapshot(harrisCountyId, "housing.home_value", "ZILLOW", LocalDate.of(year, 1, 1), Map.of("value", 200000 + year));
        }

        mvc.perform(get("/api/area/county/{geoId}/history", "48201")
                        .queryParam("category", "housing.home_value")
                        .queryParam("periods", "12")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("housing.home_value"))
                .andExpect(jsonPath("$.points.length()").value(12))
                .andExpect(jsonPath("$.points[0].snapshotPeriod").value("2014-01-01"))
                .andExpect(jsonPath("$.points[11].snapshotPeriod").value("2025-01-01"));
    }

    @Test
    void childrenEndpointReturnsImmediateChildrenWithCoverageCounts() throws Exception {
        insertSnapshot(harrisCountyId, "housing.home_value", "ZILLOW", LocalDate.of(2025, 1, 1), Map.of("value", 310000));

        mvc.perform(get("/api/area/state/{geoId}/children", "48").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children").isArray());
    }

    @Test
    void areaEndpointsReturn404ForUnknownGeo() throws Exception {
        mvc.perform(get("/api/area/state/{geoId}", UUID.randomUUID()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));

        mvc.perform(get("/api/area/state/{geoId}/history", UUID.randomUUID())
                        .queryParam("category", "housing.home_value")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/area/state/{geoId}/children", UUID.randomUUID()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void areaEndpointsReturn400ForInvalidInputs() throws Exception {
        mvc.perform(get("/api/area/not-a-level/{geoId}", "48").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));

        mvc.perform(get("/api/area/state/{geoId}/history", "48")
                        .queryParam("category", "housing.home_value")
                        .queryParam("periods", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"));
    }

    @Test
    void searchEndpointReturnsRankedResultsForSupportedLevel() throws Exception {
        mvc.perform(get("/api/area/search")
                        .queryParam("q", "Harris")
                        .queryParam("level", "county")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("Harris"))
                .andExpect(jsonPath("$.level").value("county"))
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void searchEndpointReturns400ForBlankQuery() throws Exception {
        mvc.perform(get("/api/area/search")
                        .queryParam("q", "   ")
                        .queryParam("level", "county")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/area/search"));
    }

    @Test
    void searchEndpointReturns400ForSingleCharacterQuery() throws Exception {
        mvc.perform(get("/api/area/search")
                        .queryParam("q", "H")
                        .queryParam("level", "county")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/area/search"));
    }

    @Test
    void searchEndpointReturns400ForUnsupportedLevel() throws Exception {
        mvc.perform(get("/api/area/search")
                        .queryParam("q", "Harris")
                        .queryParam("level", "neighborhood")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/area/search"));
    }

    @Test
    void publicCorsPreflightAllowsConfiguredOrigin() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/area/search")
                        .header("Origin", "https://aboutmyarea.us")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "https://aboutmyarea.us"));
    }

    private void insertSnapshot(UUID geoId, String category, String source, LocalDate period, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        jdbc.update(
                """
                insert into fdp_geo.area_snapshot
                    (geo_id, category, snapshot_period, source, is_rollup, payload)
                values (?, ?, ?, ?, false, cast(? as jsonb))
                on conflict (geo_id, category, snapshot_period, source) do update
                    set payload = excluded.payload
                """,
                geoId, category, period, source, json
        );
    }

    private void insertChangeLog(UUID geoId, String category, LocalDate priorPeriod, LocalDate currentPeriod,
                                 double pctChange, String direction, String magnitude) {
        jdbc.update(
                """
                insert into fdp_geo.area_change_log
                    (geo_id, category, prior_period, current_period, pct_change, direction, magnitude)
                values (?, ?, ?, ?, ?, cast(? as fdp_geo.change_direction), ?)
                on conflict (geo_id, category, current_period) do update
                    set pct_change = excluded.pct_change,
                        direction = excluded.direction,
                        magnitude = excluded.magnitude
                """,
                geoId, category, priorPeriod, currentPeriod, pctChange, direction, magnitude
        );
    }
}
