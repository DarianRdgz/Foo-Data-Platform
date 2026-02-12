CREATE TABLE IF NOT EXISTS product_catalog (
  product_id     VARCHAR(64) PRIMARY KEY,
  upc            VARCHAR(32),
  description    VARCHAR(512),

  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_catalog_upc ON product_catalog(upc);

-- Price snapshot at a moment in time
CREATE TABLE IF NOT EXISTS price_observation (
  id             BIGSERIAL PRIMARY KEY,
  product_id     VARCHAR(64) NOT NULL REFERENCES product_catalog(product_id),
  location_id    VARCHAR(64) NOT NULL,

  -- what we observed
  regular_price  NUMERIC(10,2),
  promo_price    NUMERIC(10,2),

  observed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_obs_product ON price_observation(product_id);
CREATE INDEX IF NOT EXISTS idx_price_obs_location_time ON price_observation(location_id, observed_at DESC);
