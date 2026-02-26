-- V6__access_control.sql
CREATE TABLE IF NOT EXISTS fdp_core.data_domain (
  id           SMALLSERIAL PRIMARY KEY,
  code         TEXT NOT NULL UNIQUE,
  description  TEXT,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO fdp_core.data_domain (code, description)
VALUES
  ('GROCERY_PRICING', 'Grocery pricing and product/location data'),
  ('PUBLIC_SAFETY', 'Public safety / crime incident datasets')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS fdp_core.data_domain_access (
  id         BIGSERIAL PRIMARY KEY,
  principal  TEXT NOT NULL,
  domain_id  SMALLINT NOT NULL REFERENCES fdp_core.data_domain(id),
  can_read   BOOLEAN NOT NULL DEFAULT TRUE,
  can_write  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (principal, domain_id)
);

CREATE TABLE IF NOT EXISTS fdp_core.principal_region_scope (
  id           BIGSERIAL PRIMARY KEY,
  principal    TEXT NOT NULL,
  country_code CHAR(2) NOT NULL,
  state_code   CHAR(2),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (principal, country_code, state_code)
);
