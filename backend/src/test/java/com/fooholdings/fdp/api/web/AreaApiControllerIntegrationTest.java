package com.fooholdings.fdp.api.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

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
        r.add("spring.datasource.url",           postgres::getJdbcUrl);
        r.add("spring.datasource.username",      postgres::getUsername);
        r.add("spring.datasource.password",      postgres::getPassword);
        r.add("spring.flyway.enabled",           () -> true);
        r.add("spring.jpa.hibernate.ddl-auto",   () -> "validate");
        r.add("fdp.geo.seeding.enabled",         () -> false);
        r.add("fdp.scheduler.disaster.enabled",  () -> false);
        r.add("fdp.scheduler.education.enabled", () -> false);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void returnsLatestSnapshotsForExistingGeo() throws Exception {
        UUID stateId = jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' " +
                "and fips_code = '48' limit 1", UUID.class);

        // Seed two snapshots for the same category — endpoint must return only the latest
        insertSnapshot(stateId, "economic.unemployment_rate", "FRED",
                LocalDate.of(2024, 12, 31), Map.of("value", 4.1));
        insertSnapshot(stateId, "economic.unemployment_rate", "FRED",
                LocalDate.of(2025, 12, 31), Map.of("value", 3.9)); // latest

        insertSnapshot(stateId, "risk.composite", "DISASTER_RISK",
                LocalDate.of(2025, 12, 31), Map.of("riskScore", 42.5, "tier", "moderate"));

        mvc.perform(get("/api/area/state/{id}", stateId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoId").value(stateId.toString()))
                .andExpect(jsonPath("$.geoLevel").value("state"))
                .andExpect(jsonPath("$.snapshots").isArray());

        // Verify only ONE row per category (the latest 2025 period, not the 2024)
        String body = mvc.perform(get("/api/area/state/{id}", stateId))
                .andReturn().getResponse().getContentAsString();
        var response = objectMapper.readTree(body);
        long unemploymentRows = response.at("/snapshots").findValues("category")
                .stream().filter(n -> "economic.unemployment_rate".equals(n.stringValue())).count();
        assertThat(unemploymentRows).isEqualTo(1L);
    }

    @Test
    void returns404ForUnknownGeo() throws Exception {
        mvc.perform(get("/api/area/state/{id}", UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    void returns404WhenGeoLevelMismatches() throws Exception {
        // geo exists as 'state' but we request it as 'county' — must be 404, not 500
        UUID stateId = jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' limit 1",
                UUID.class);

        mvc.perform(get("/api/area/county/{id}", stateId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void insertSnapshot(UUID geoId, String category, String source,
                                LocalDate period, Map<String, Object> payload) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        jdbc.update(
                """
                insert into fdp_geo.area_snapshot
                    (geo_id, category, snapshot_period, source, is_rollup, payload)
                values (?, ?, ?, ?, false, CAST(? AS jsonb))
                on conflict (geo_id, category, snapshot_period, source) do update
                    set payload = excluded.payload
                """,
                geoId, category, period, source, json);
    }
}
