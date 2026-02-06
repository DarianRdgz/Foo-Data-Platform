CREATE TABLE IF NOT EXISTS store_location (
  location_id      VARCHAR(64) PRIMARY KEY,
  name             VARCHAR(255),
  address_line1    VARCHAR(255),
  city             VARCHAR(120),
  state            VARCHAR(32),
  zip_code         VARCHAR(20),

  -- audit fields
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- For searching by geography later
CREATE INDEX IF NOT EXISTS idx_store_location_zip ON store_location(zip_code);
CREATE INDEX IF NOT EXISTS idx_store_location_state ON store_location(state);
