-- V4__grocery_model.sql
CREATE TABLE IF NOT EXISTS fdp_grocery.unit_of_measure (
  id              SMALLSERIAL PRIMARY KEY,
  code            TEXT NOT NULL UNIQUE,
  display_name    TEXT NOT NULL,
  unit_type       TEXT NOT NULL,
  to_base_factor  NUMERIC(18,8),
  base_code       TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO fdp_grocery.unit_of_measure (code, display_name, unit_type, to_base_factor, base_code)
VALUES
  ('ea', 'Each', 'COUNT', 1, 'ea'),
  ('oz', 'Ounce', 'WEIGHT', 28.349523125, 'g'),
  ('lb', 'Pound', 'WEIGHT', 453.59237, 'g'),
  ('g',  'Gram',  'WEIGHT', 1, 'g'),
  ('kg', 'Kilogram', 'WEIGHT', 1000, 'g'),
  ('ml', 'Milliliter', 'VOLUME', 1, 'ml'),
  ('l',  'Liter', 'VOLUME', 1000, 'ml')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS fdp_grocery.store_location (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system_id   SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  source_location_id TEXT NOT NULL,
  chain_code         TEXT,
  name               TEXT NOT NULL,
  phone              TEXT,
  address_line1      TEXT,
  address_line2      TEXT,
  city               TEXT,
  state_code         CHAR(2),
  postal_code        TEXT,
  geo_region_id      BIGINT REFERENCES fdp_core.geo_region(id),
  latitude           NUMERIC(9,6),
  longitude          NUMERIC(9,6),
  store_number       TEXT,
  division_number    TEXT,
  hours_json         JSONB,
  departments_json   JSONB,
  is_active          BOOLEAN NOT NULL DEFAULT TRUE,
  first_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source_system_id, source_location_id)
);
CREATE INDEX IF NOT EXISTS idx_store_location_geo_region
  ON fdp_grocery.store_location (geo_region_id);
CREATE INDEX IF NOT EXISTS idx_store_location_state_postal
  ON fdp_grocery.store_location (state_code, postal_code);

CREATE TABLE IF NOT EXISTS fdp_grocery.product (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  canonical_name   TEXT NOT NULL,
  brand            TEXT,
  description      TEXT,
  category_path    TEXT,
  upc              TEXT,
  country_origin   TEXT,
  attributes_json  JSONB,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_product_upc ON fdp_grocery.product (upc);
CREATE INDEX IF NOT EXISTS idx_product_name_lower ON fdp_grocery.product ((lower(canonical_name)));

CREATE TABLE IF NOT EXISTS fdp_grocery.source_product (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system_id  SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  source_product_id TEXT NOT NULL,
  product_id        UUID REFERENCES fdp_grocery.product(id),
  upc               TEXT,
  name              TEXT NOT NULL,
  brand             TEXT,
  product_page_uri  TEXT,
  raw_category_json JSONB,
  raw_flags_json    JSONB,
  first_seen_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (source_system_id, source_product_id)
);
CREATE INDEX IF NOT EXISTS idx_source_product_upc ON fdp_grocery.source_product (upc);
CREATE INDEX IF NOT EXISTS idx_source_product_product_id ON fdp_grocery.source_product (product_id);

CREATE TABLE IF NOT EXISTS fdp_grocery.price_observation (
  id                BIGSERIAL PRIMARY KEY,
  source_system_id  SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  store_location_id UUID NOT NULL REFERENCES fdp_grocery.store_location(id),
  source_product_pk UUID NOT NULL REFERENCES fdp_grocery.source_product(id),
  observed_at       TIMESTAMPTZ NOT NULL,
  currency_code     CHAR(3) NOT NULL DEFAULT 'USD',
  price             NUMERIC(10,2) NOT NULL,
  regular_price     NUMERIC(10,2),
  promo_price       NUMERIC(10,2),
  is_on_sale        BOOLEAN,
  unit_price        NUMERIC(10,4),
  uom_id            SMALLINT REFERENCES fdp_grocery.unit_of_measure(id),
  unit_quantity     NUMERIC(18,6),
  availability      TEXT,
  ingestion_run_id  UUID REFERENCES fdp_core.ingestion_run(id),
  raw_payload_id    UUID REFERENCES fdp_core.raw_payload(id),
  UNIQUE (source_system_id, store_location_id, source_product_pk, observed_at)
);
CREATE INDEX IF NOT EXISTS idx_price_obs_store_prod_time
  ON fdp_grocery.price_observation (store_location_id, source_product_pk, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_obs_product_time
  ON fdp_grocery.price_observation (source_product_pk, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_obs_time
  ON fdp_grocery.price_observation (observed_at DESC);

-- FUTURE (Story 4.4): partition price_observation by observed_at (monthly) when volume justifies.
