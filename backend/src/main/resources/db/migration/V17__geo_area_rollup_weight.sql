-- V17__geo_area_rollup_weight.sql
-- Story 5.5 support migration for weighted geo rollups.
-- If no source-specific weight is available yet, rows can remain NULL and the
-- service will fall back to 1.0. To truly satisfy the weighted-average acceptance
-- criterion, populate this column with a real weight such as housing units or population.
 
ALTER TABLE fdp_geo.geo_areas
    ADD COLUMN IF NOT EXISTS rollup_weight NUMERIC(18,6);
 
COMMENT ON COLUMN fdp_geo.geo_areas.rollup_weight
    IS 'Optional weighting factor used by GeoRollupService. NULL means fall back to weight 1.0.';