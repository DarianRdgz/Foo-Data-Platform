-- V16__area_change_log_magnitude.sql
-- Story 5.5 — GeoChangeDetectionService
--
-- Adds the magnitude label (slight|moderate|significant) to area_change_log.
-- This column is written by GeoChangeDetectionService and read directly by
-- the API so that change badges never require a join back to area_snapshot.
--
-- Thresholds (same as GeoChangeDetectionService.java):
--   |pct_change| <  1.0  → slight
--   |pct_change| <  5.0  → moderate
--   |pct_change| >= 5.0  → significant
 
ALTER TABLE fdp_geo.area_change_log
    ADD COLUMN IF NOT EXISTS magnitude TEXT NOT NULL DEFAULT 'slight'
        CONSTRAINT chk_area_change_log_magnitude
            CHECK (magnitude IN ('slight', 'moderate', 'significant'));
 
COMMENT ON COLUMN fdp_geo.area_change_log.magnitude
    IS 'Human-readable severity label derived from |pct_change|: slight (<1%), moderate (<5%), significant (≥5%)';