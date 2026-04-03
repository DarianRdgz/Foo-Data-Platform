package com.fooholdings.fdp.api.web;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MapTileApiControllerIntegrationTest {

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
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private UUID texasStateId;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() throws Exception {
        jdbc.update("delete from fdp_geo.area_snapshot where source in ('DISASTER_RISK','ZILLOW')");

        texasStateId = jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' and fips_code = '48' limit 1",
                UUID.class);

        insertSnapshot(texasStateId, "risk.composite", "DISASTER_RISK", LocalDate.of(2025, 1, 1),
                Map.of("riskScore", 42.5, "tier", "moderate"));
    }

    @Test
    void mapTilesReturnsFeatureCollectionForValidBbox() throws Exception {
        mvc.perform(get("/api/map/tiles/state").queryParam("bbox", "-180,-90,180,90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features.length()").value(51));
    }

    @Test
    void mapTilesReturns400ForMalformedBbox() throws Exception {
        mvc.perform(get("/api/map/tiles/state").queryParam("bbox", "bad-bbox"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/map/tiles/state").queryParam("bbox", "1,2,3"))
                .andExpect(status().isBadRequest());
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
}
