package com.fooholdings.fdp.geo.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class GeoAreaJdbcRepository {

    private final JdbcTemplate jdbc;

    public GeoAreaJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findNationalGeoId() {
        return jdbc.query(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'national' limit 1",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty());
    }

    public Optional<UUID> findStateGeoIdByFips(String stateFips2) {
        return jdbc.query(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'state' and fips_code = ?",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                stateFips2);
    }

    public Optional<UUID> findCountyGeoIdByFips(String countyFips5) {
        return jdbc.query(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'county' and fips_code = ?",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                countyFips5);
    }

    public Optional<UUID> findCountyGeoIdByNameAndStateFips(String countyName, String stateFips2) {
        return jdbc.query(
                """
                select c.geo_id
                from fdp_geo.geo_areas c
                join fdp_geo.geo_areas s on s.geo_id = c.parent_geo_id
                where c.geo_level = 'county'
                  and s.geo_level = 'state'
                  and lower(c.name) = lower(?)
                  and s.fips_code = ?
                limit 1
                """,
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                countyName, stateFips2
        );
    }

    public Optional<UUID> findCityGeoIdByNameAndStateFips(String cityName, String stateFips2) {
        return jdbc.query(
                """
                select c.geo_id
                from fdp_geo.geo_areas c
                join fdp_geo.geo_areas county on county.geo_id = c.parent_geo_id
                join fdp_geo.geo_areas s on s.geo_id = county.parent_geo_id
                where c.geo_level = 'city'
                  and county.geo_level = 'county'
                  and s.geo_level = 'state'
                  and lower(c.name) = lower(?)
                  and s.fips_code = ?
                limit 1
                """,
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                cityName, stateFips2
        );
    }

    public Optional<UUID> findZipGeoIdByZipCode(String zipCode) {
        return jdbc.query(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'zip' and zip_code = ?",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                zipCode
        );
    }

    public Optional<UUID> findMetroGeoIdByCbsaCode(String cbsaCode) {
        return jdbc.query(
                "select geo_id from fdp_geo.geo_areas where geo_level = 'metro' and cbsa_code = ?",
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                cbsaCode
        );
    }

    public Optional<UUID> findMetroGeoIdByName(String metroName) {
        return jdbc.query(
                """
                select geo_id
                from fdp_geo.geo_areas
                where geo_level = 'metro'
                  and lower(name) = lower(?)
                limit 1
                """,
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                metroName
        );
    }

    /**
     * Finds a state geo_id by case-insensitive name match.
     * Used by CdeAdapter to resolve raw state strings from FBI CSV files.
     */
    public Optional<UUID> findStateGeoIdByName(String stateName) {
        return jdbc.query(
                """
                select geo_id
                from fdp_geo.geo_areas
                where geo_level = 'state'
                and lower(name) = lower(?)
                limit 1
                """,
                rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(),
                stateName
        );
    }

    /**
     * Generic geo lookup by level + key type + key value.
     * Used by FredAdapter to resolve series definitions from the FRED catalog at runtime,
     * keeping UUIDs out of configuration files.
     *
     * @param geoLevel    one of: national, state, county, metro, zip
     * @param geoKeyType  one of: none, fips, cbsa, name, zip
     * @param geoKey      the actual key value (e.g. "48" for Texas FIPS, "US" for national)
     */
    public Optional<UUID> findGeoId(String geoLevel, String geoKeyType, String geoKey) {
        if (geoLevel == null || geoKeyType == null) {
            return Optional.empty();
        }
        return switch (geoLevel) {
            case "national" -> findNationalGeoId();
            case "state"   -> switch (geoKeyType) {
                case "fips" -> findStateGeoIdByFips(geoKey);
                case "name" -> findStateGeoIdByName(geoKey);
                default     -> Optional.empty();
            };
            case "county"  -> switch (geoKeyType) {
                case "fips" -> findCountyGeoIdByFips(geoKey);
                default     -> Optional.empty();
            };
            case "metro"   -> switch (geoKeyType) {
                case "cbsa" -> findMetroGeoIdByCbsaCode(geoKey);
                case "name" -> findMetroGeoIdByName(geoKey);
                default     -> Optional.empty();
            };
            case "zip"     -> switch (geoKeyType) {
                case "zip"  -> findZipGeoIdByZipCode(geoKey);
                default     -> Optional.empty();
            };
            default -> Optional.empty();
        };
    }

    public UUID upsertGeoArea(
            String geoLevel,
            String fipsCode,
            String cbsaCode,
            String zipCode,
            String name,
            UUID parentGeoId,
            String displayLabel
    ) {
        UUID inserted = jdbc.query(
                """
                insert into fdp_geo.geo_areas (geo_level, fips_code, cbsa_code, zip_code, name, parent_geo_id, display_label)
                values (?::fdp_geo.geo_level, ?, ?, ?, ?, ?, ?)
                on conflict do nothing
                returning geo_id
                """,
                rs -> rs.next() ? UUID.fromString(rs.getString("geo_id")) : null,
                geoLevel, fipsCode, cbsaCode, zipCode, name, parentGeoId, displayLabel
        );

        if (inserted != null) {
            return inserted;
        }

        if (zipCode != null && !zipCode.isBlank()) {
            return jdbc.queryForObject(
                    "select geo_id from fdp_geo.geo_areas where geo_level = ?::fdp_geo.geo_level and zip_code = ?",
                    new UuidMapper(), geoLevel, zipCode
            );
        }
        if (cbsaCode != null && !cbsaCode.isBlank()) {
            return jdbc.queryForObject(
                    "select geo_id from fdp_geo.geo_areas where geo_level = ?::fdp_geo.geo_level and cbsa_code = ?",
                    new UuidMapper(), geoLevel, cbsaCode
            );
        }
        if (fipsCode != null && !fipsCode.isBlank()) {
            return jdbc.queryForObject(
                    "select geo_id from fdp_geo.geo_areas where geo_level = ?::fdp_geo.geo_level and fips_code = ?",
                    new UuidMapper(), geoLevel, fipsCode
            );
        }

        return jdbc.queryForObject(
                "select geo_id from fdp_geo.geo_areas where geo_level = ?::fdp_geo.geo_level and name = ? and parent_geo_id is not distinct from ?",
                new UuidMapper(), geoLevel, name, parentGeoId
        );
    }

    private static final class UuidMapper implements RowMapper<UUID> {
        @Override
        public UUID mapRow(ResultSet rs, int rowNum) throws SQLException {
            return UUID.fromString(rs.getString(1));
        }
    }
}