package com.fooholdings.fdp.geo.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest
class DisasterRiskScoreServiceIntegrationTest {

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

    @Autowired DisasterRiskScoreService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void doesNotWriteCompositeWhenOnlyOnSourcePresent() throws Exception {
        UUID geoId = stateGeoId();
        insertSnapshot(geoId, "risk.disaster.fema", "FEMA",
                Map.of("disasterCountLast10yr", 10, "mostCommonDisasterType", "Flood"));

        service.recomputeAll();

        // NOAA is absent — no composite should be written
        Integer compositeRows = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'DISASTER_RISK'",
                Integer.class);
        assertThat(compositeRows).isZero();
    }

    @Test
    void writesCompositeWhenBothSourcesPresent() throws Exception {
        UUID geoId = stateGeoId();
        insertSnapshot(geoId, "risk.disaster.fema", "FEMA",
                Map.of("disasterCountLast10yr", 20, "mostCommonDisasterType", "Severe Storm(s)"));
        insertSnapshot(geoId, "risk.disaster.noaa", "NOAA",
                Map.of("stormEventsCount", 15, "estimatedDamageUsd", 5_000_000.0, "fatalities", 3));

        service.recomputeAll();

        String payload = jdbc.queryForObject(
                "select payload::text from fdp_geo.area_snapshot where source = 'DISASTER_RISK' limit 1",
                String.class);
        assertThat(payload)
                .contains("\"riskScore\"")
                .contains("\"tier\"")
                .contains("\"scoringVersion\":\"v1\"");
    }

    @Test
    void tierIsWithinValidValues() throws Exception {
        UUID geoId = stateGeoId();
        // High counts → severe tier
        insertSnapshot(geoId, "risk.disaster.fema", "FEMA",
                Map.of("disasterCountLast10yr", 100));
        insertSnapshot(geoId, "risk.disaster.noaa", "NOAA",
                Map.of("stormEventsCount", 100, "estimatedDamageUsd", 50_000_000.0, "fatalities", 20));

        service.recomputeAll();

        String tier = jdbc.queryForObject(
                "select payload->>'tier' from fdp_geo.area_snapshot where source = 'DISASTER_RISK'",
                String.class);
        assertThat(tier).isIn("low", "moderate", "high", "severe");
    }

    @Test
    void recomputeAllIsIdempotent() throws Exception {
        UUID geoId = stateGeoId();
        insertSnapshot(geoId, "risk.disaster.fema", "FEMA",
                Map.of("disasterCountLast10yr", 5));
        insertSnapshot(geoId, "risk.disaster.noaa", "NOAA",
                Map.of("stormEventsCount", 5, "estimatedDamageUsd", 0.0, "fatalities", 0));

        service.recomputeAll();
        Integer first = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'DISASTER_RISK'",
                Integer.class);

        service.recomputeAll();
        Integer second = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where source = 'DISASTER_RISK'",
                Integer.class);

        assertThat(second).isEqualTo(first);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private UUID stateGeoId() {
        return jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' limit 1",
                UUID.class);
    }

    private void insertSnapshot(UUID geoId, String category, String source,
                                Map<String, Object> payload) throws Exception {
        LocalDate period = LocalDate.of(LocalDate.now().getYear(), 12, 31);
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
