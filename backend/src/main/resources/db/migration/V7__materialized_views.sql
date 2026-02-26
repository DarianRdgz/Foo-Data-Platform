-- V7__materialized_views.sql
-- Monolith rollups. Replace with analytics service / warehouse after switching to microservice

CREATE MATERIALIZED VIEW IF NOT EXISTS fdp_grocery.mv_state_daily_avg_price AS
SELECT
  gr.country_code,
  gr.state_code,
  p.id AS product_id,
  date_trunc('day', po.observed_at) AS day,
  AVG(po.price) AS avg_price,
  COUNT(*) AS sample_size
FROM fdp_grocery.price_observation po
JOIN fdp_grocery.store_location sl ON sl.id = po.store_location_id
JOIN fdp_core.geo_region gr ON gr.id = sl.geo_region_id
JOIN fdp_grocery.source_product sp ON sp.id = po.source_product_pk
JOIN fdp_grocery.product p ON p.id = sp.product_id
GROUP BY gr.country_code, gr.state_code, p.id, date_trunc('day', po.observed_at);

CREATE INDEX IF NOT EXISTS idx_mv_state_daily_avg_price_lookup
  ON fdp_grocery.mv_state_daily_avg_price (country_code, state_code, product_id, day DESC);

CREATE MATERIALIZED VIEW IF NOT EXISTS fdp_grocery.mv_country_daily_avg_price AS
SELECT
  gr.country_code,
  p.id AS product_id,
  date_trunc('day', po.observed_at) AS day,
  AVG(po.price) AS avg_price,
  COUNT(*) AS sample_size
FROM fdp_grocery.price_observation po
JOIN fdp_grocery.store_location sl ON sl.id = po.store_location_id
JOIN fdp_core.geo_region gr ON gr.id = sl.geo_region_id
JOIN fdp_grocery.source_product sp ON sp.id = po.source_product_pk
JOIN fdp_grocery.product p ON p.id = sp.product_id
GROUP BY gr.country_code, p.id, date_trunc('day', po.observed_at);

CREATE INDEX IF NOT EXISTS idx_mv_country_daily_avg_price_lookup
  ON fdp_grocery.mv_country_daily_avg_price (country_code, product_id, day DESC);

-- Refresh outside Flyway:
--   REFRESH MATERIALIZED VIEW CONCURRENTLY fdp_grocery.mv_state_daily_avg_price;
--   REFRESH MATERIALIZED VIEW CONCURRENTLY fdp_grocery.mv_country_daily_avg_price;
