-- V11__fdp_geo_hierarchy_schema.sql
-- Sprint 5 / Story 5.1
-- Creates the V5 geo hierarchy registry + unified snapshot model.

CREATE SCHEMA IF NOT EXISTS fdp_geo;

-- ── Enums ───────────────────────────────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                 WHERE t.typname = 'geo_level' AND n.nspname = 'fdp_geo') THEN
    CREATE TYPE fdp_geo.geo_level AS ENUM ('national', 'state', 'metro', 'county', 'city', 'zip');
  END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
                 WHERE t.typname = 'change_direction' AND n.nspname = 'fdp_geo') THEN
    CREATE TYPE fdp_geo.change_direction AS ENUM ('up', 'down', 'flat');
  END IF;
END $$;

-- ── Geo registry (single source of truth) ───────────────────────────────
CREATE TABLE IF NOT EXISTS fdp_geo.geo_areas (
  geo_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  geo_level      fdp_geo.geo_level NOT NULL,

  -- Codes used for deterministic lookups.
  -- (Not all levels use all codes; keep nullable.)
  fips_code      TEXT,
  cbsa_code      TEXT,
  zip_code       TEXT,

  name           TEXT NOT NULL,
  parent_geo_id  UUID REFERENCES fdp_geo.geo_areas(geo_id),

  -- Frontend-friendly label (ex: "Harris County, TX" or "Houston, TX")
  display_label  TEXT NOT NULL,

  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- Prevent duplicate natural keys per level
  CONSTRAINT uq_geo_areas_level_fips UNIQUE (geo_level, fips_code),
  CONSTRAINT uq_geo_areas_level_cbsa UNIQUE (geo_level, cbsa_code),
  CONSTRAINT uq_geo_areas_level_zip  UNIQUE (geo_level, zip_code)
);

-- Helpful lookup indexes
CREATE INDEX IF NOT EXISTS idx_geo_areas_level ON fdp_geo.geo_areas (geo_level);
CREATE INDEX IF NOT EXISTS idx_geo_areas_parent ON fdp_geo.geo_areas (parent_geo_id);
CREATE INDEX IF NOT EXISTS idx_geo_areas_name_lower ON fdp_geo.geo_areas ((lower(name)));

-- ── Unified snapshot model ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS fdp_geo.area_snapshot (
  id             BIGSERIAL PRIMARY KEY,
  geo_id         UUID NOT NULL REFERENCES fdp_geo.geo_areas(geo_id),
  category       TEXT NOT NULL,
  snapshot_period DATE NOT NULL,
  source         TEXT NOT NULL,
  is_rollup       BOOLEAN NOT NULL DEFAULT FALSE,
  payload         JSONB NOT NULL,
  ingested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_area_snapshot UNIQUE (geo_id, category, snapshot_period, source)
);

-- Primary query pattern: "latest snapshot(s) for geo + category"
CREATE INDEX IF NOT EXISTS idx_area_snapshot_geo_cat_period
  ON fdp_geo.area_snapshot (geo_id, category, snapshot_period DESC);
CREATE INDEX IF NOT EXISTS idx_area_snapshot_geo_period
  ON fdp_geo.area_snapshot (geo_id, snapshot_period DESC);

-- ── Change log (computed, not ingested) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS fdp_geo.area_change_log (
  id              BIGSERIAL PRIMARY KEY,
  geo_id          UUID NOT NULL REFERENCES fdp_geo.geo_areas(geo_id),
  category        TEXT NOT NULL,
  prior_period    DATE NOT NULL,
  current_period  DATE NOT NULL,
  pct_change      NUMERIC(12,6) NOT NULL,
  direction       fdp_geo.change_direction NOT NULL,
  computed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_area_change_log UNIQUE (geo_id, category, current_period)
);
CREATE INDEX IF NOT EXISTS idx_area_change_geo_cat_period
  ON fdp_geo.area_change_log (geo_id, category, current_period DESC);

-- ── Baseline seed: national + states + DC ───────────────────────────────
-- Note: Full metro/county/city/zip seeding is performed by GeoSeedService (Story 5.1).

-- National (single hard-coded record)
INSERT INTO fdp_geo.geo_areas (geo_id, geo_level, name, display_label)
VALUES ('00000000-0000-0000-0000-000000000000', 'national', 'United States', 'United States')
ON CONFLICT (geo_id) DO NOTHING;

-- States + DC. fips_code is 2-digit state FIPS.
INSERT INTO fdp_geo.geo_areas (geo_level, fips_code, name, parent_geo_id, display_label)
VALUES
  ('state','01','Alabama','00000000-0000-0000-0000-000000000000','Alabama'),
  ('state','02','Alaska','00000000-0000-0000-0000-000000000000','Alaska'),
  ('state','04','Arizona','00000000-0000-0000-0000-000000000000','Arizona'),
  ('state','05','Arkansas','00000000-0000-0000-0000-000000000000','Arkansas'),
  ('state','06','California','00000000-0000-0000-0000-000000000000','California'),
  ('state','08','Colorado','00000000-0000-0000-0000-000000000000','Colorado'),
  ('state','09','Connecticut','00000000-0000-0000-0000-000000000000','Connecticut'),
  ('state','10','Delaware','00000000-0000-0000-0000-000000000000','Delaware'),
  ('state','11','District of Columbia','00000000-0000-0000-0000-000000000000','District of Columbia'),
  ('state','12','Florida','00000000-0000-0000-0000-000000000000','Florida'),
  ('state','13','Georgia','00000000-0000-0000-0000-000000000000','Georgia'),
  ('state','15','Hawaii','00000000-0000-0000-0000-000000000000','Hawaii'),
  ('state','16','Idaho','00000000-0000-0000-0000-000000000000','Idaho'),
  ('state','17','Illinois','00000000-0000-0000-0000-000000000000','Illinois'),
  ('state','18','Indiana','00000000-0000-0000-0000-000000000000','Indiana'),
  ('state','19','Iowa','00000000-0000-0000-0000-000000000000','Iowa'),
  ('state','20','Kansas','00000000-0000-0000-0000-000000000000','Kansas'),
  ('state','21','Kentucky','00000000-0000-0000-0000-000000000000','Kentucky'),
  ('state','22','Louisiana','00000000-0000-0000-0000-000000000000','Louisiana'),
  ('state','23','Maine','00000000-0000-0000-0000-000000000000','Maine'),
  ('state','24','Maryland','00000000-0000-0000-0000-000000000000','Maryland'),
  ('state','25','Massachusetts','00000000-0000-0000-0000-000000000000','Massachusetts'),
  ('state','26','Michigan','00000000-0000-0000-0000-000000000000','Michigan'),
  ('state','27','Minnesota','00000000-0000-0000-0000-000000000000','Minnesota'),
  ('state','28','Mississippi','00000000-0000-0000-0000-000000000000','Mississippi'),
  ('state','29','Missouri','00000000-0000-0000-0000-000000000000','Missouri'),
  ('state','30','Montana','00000000-0000-0000-0000-000000000000','Montana'),
  ('state','31','Nebraska','00000000-0000-0000-0000-000000000000','Nebraska'),
  ('state','32','Nevada','00000000-0000-0000-0000-000000000000','Nevada'),
  ('state','33','New Hampshire','00000000-0000-0000-0000-000000000000','New Hampshire'),
  ('state','34','New Jersey','00000000-0000-0000-0000-000000000000','New Jersey'),
  ('state','35','New Mexico','00000000-0000-0000-0000-000000000000','New Mexico'),
  ('state','36','New York','00000000-0000-0000-0000-000000000000','New York'),
  ('state','37','North Carolina','00000000-0000-0000-0000-000000000000','North Carolina'),
  ('state','38','North Dakota','00000000-0000-0000-0000-000000000000','North Dakota'),
  ('state','39','Ohio','00000000-0000-0000-0000-000000000000','Ohio'),
  ('state','40','Oklahoma','00000000-0000-0000-0000-000000000000','Oklahoma'),
  ('state','41','Oregon','00000000-0000-0000-0000-000000000000','Oregon'),
  ('state','42','Pennsylvania','00000000-0000-0000-0000-000000000000','Pennsylvania'),
  ('state','44','Rhode Island','00000000-0000-0000-0000-000000000000','Rhode Island'),
  ('state','45','South Carolina','00000000-0000-0000-0000-000000000000','South Carolina'),
  ('state','46','South Dakota','00000000-0000-0000-0000-000000000000','South Dakota'),
  ('state','47','Tennessee','00000000-0000-0000-0000-000000000000','Tennessee'),
  ('state','48','Texas','00000000-0000-0000-0000-000000000000','Texas'),
  ('state','49','Utah','00000000-0000-0000-0000-000000000000','Utah'),
  ('state','50','Vermont','00000000-0000-0000-0000-000000000000','Vermont'),
  ('state','51','Virginia','00000000-0000-0000-0000-000000000000','Virginia'),
  ('state','53','Washington','00000000-0000-0000-0000-000000000000','Washington'),
  ('state','54','West Virginia','00000000-0000-0000-0000-000000000000','West Virginia'),
  ('state','55','Wisconsin','00000000-0000-0000-0000-000000000000','Wisconsin'),
  ('state','56','Wyoming','00000000-0000-0000-0000-000000000000','Wyoming')
ON CONFLICT DO NOTHING;