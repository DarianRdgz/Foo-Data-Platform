-- V2__core_dimensions.sql
CREATE TABLE IF NOT EXISTS fdp_core.source_system (
  id           SMALLSERIAL PRIMARY KEY,
  code         TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS fdp_core.geo_region (
  id           BIGSERIAL PRIMARY KEY,
  country_code VARCHAR(2) NOT NULL,
  state_code   VARCHAR(2),
  county_name  TEXT,
  city_name    TEXT,
  postal_code  TEXT,
  latitude     NUMERIC(9,6),
  longitude    NUMERIC(9,6),
  geohash      TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_geo_region_country_state
  ON fdp_core.geo_region (country_code, state_code);
CREATE INDEX IF NOT EXISTS idx_geo_region_postal_code
  ON fdp_core.geo_region (postal_code);
CREATE INDEX IF NOT EXISTS idx_geo_region_city_state
  ON fdp_core.geo_region (city_name, state_code);
