package com.fooholdings.fdp.geo;

import java.time.LocalDate;
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

@Testcontainers
@SpringBootTest
class GeoSchemaIntegrationTest {

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
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void areaSnapshot_acceptsRowsForAllGeoLevels() {
        Integer zipVarchars = jdbc.queryForObject(
                """
                select count(*)
                from information_schema.columns
                where table_schema = 'fdp_geo'
                  and column_name = 'zip_code'
                  and data_type = 'character varying'
                """,
                Integer.class
        );
        assertThat(zipVarchars).isZero();

        UUID national = findNationalGeoId();
        UUID state = findStateGeoIdByFips("48");

        // Create unique test-only rows for non-seeded levels
        UUID metro = insertGeoArea(
                "metro",
                null,
                "99991",
                null,
                "Test Metro",
                state,
                "Test Metro"
        );

        UUID county = insertGeoArea(
                "county",
                "99901",
                null,
                null,
                "Test County",
                state,
                "Test County"
        );

        UUID city = insertGeoArea(
                "city",
                null,
                null,
                null,
                "Test City",
                county,
                "Test City, TX"
        );

        UUID zip = insertGeoArea(
                "zip",
                null,
                null,
                "99999",
                "99999",
                county,
                "99999"
        );

        LocalDate period = LocalDate.of(2026, 3, 1);

        insertSnapshot(national, "economic.unemployment_rate", period);
        insertSnapshot(state, "economic.unemployment_rate", period);
        insertSnapshot(metro, "housing.home_value", period);
        insertSnapshot(county, "risk.composite", period);
        insertSnapshot(city, "education.postsecondary.school_count", period);
        insertSnapshot(zip, "grocery.kroger.avg_cart", period);

        Integer rows = jdbc.queryForObject(
                "select count(*) from fdp_geo.area_snapshot where snapshot_period = ?",
                Integer.class,
                period
        );

        assertThat(rows).isEqualTo(6);
    }

    private UUID findNationalGeoId() {
        return jdbc.queryForObject(
                """
                select geo_id
                from fdp_geo.geo_areas
                where geo_level = 'national'
                limit 1
                """,
                UUID.class
        );
    }

    private UUID findStateGeoIdByFips(String fips) {
        return jdbc.queryForObject(
                """
                select geo_id
                from fdp_geo.geo_areas
                where geo_level = 'state'
                  and fips_code = ?
                """,
                UUID.class,
                fips
        );
    }

    private UUID insertGeoArea(
            String level,
            String fipsCode,
            String cbsaCode,
            String zipCode,
            String name,
            UUID parentGeoId,
            String displayLabel
    ) {
        return jdbc.queryForObject(
                """
                insert into fdp_geo.geo_areas
                    (geo_level, fips_code, cbsa_code, zip_code, name, parent_geo_id, display_label)
                values
                    (?::fdp_geo.geo_level, ?, ?, ?, ?, ?, ?)
                returning geo_id
                """,
                UUID.class,
                level,
                fipsCode,
                cbsaCode,
                zipCode,
                name,
                parentGeoId,
                displayLabel
        );
    }

    private void insertSnapshot(UUID geoId, String category, LocalDate period) {
        jdbc.update(
                """
                insert into fdp_geo.area_snapshot
                    (geo_id, category, snapshot_period, source, is_rollup, payload)
                values
                    (?, ?, ?, ?, false, '{"value":1.0}'::jsonb)
                """,
                geoId,
                category,
                period,
                "TestSource"
        );
    }
}