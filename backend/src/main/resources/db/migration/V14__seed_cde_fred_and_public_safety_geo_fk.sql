-- Seed source systems
INSERT INTO fdp_core.source_system (code, display_name)
VALUES
  ('CDE',  'FBI Crime Data Explorer'),
  ('FRED', 'Federal Reserve Economic Data')
ON CONFLICT (code) DO NOTHING;

-- Bridge crime_incident to the Sprint 5 geo model
ALTER TABLE fdp_public_safety.crime_incident
  ADD COLUMN IF NOT EXISTS geo_id UUID;

-- No backfill: crime_incident has no pre-existing rows (CDE has not run).
-- If rows exist from another source, add a targeted UPDATE here after
-- inspecting the actual fdp_core.geo_region column names.

DO $$
DECLARE
  total_count    BIGINT;
  unresolved_count BIGINT;
BEGIN
  SELECT COUNT(*) INTO total_count FROM fdp_public_safety.crime_incident;
  SELECT COUNT(*) INTO unresolved_count 
  FROM fdp_public_safety.crime_incident WHERE geo_id IS NULL;

  IF total_count = 0 THEN
    RAISE NOTICE 'V14 migration: crime_incident is empty, no backfill required.';
  ELSE
    RAISE NOTICE 'V14 migration: % of % crime_incident rows have null geo_id.',
                 unresolved_count, total_count;
  END IF;
END $$;

ALTER TABLE fdp_public_safety.crime_incident
  ADD CONSTRAINT fk_crime_incident_geo_id
  FOREIGN KEY (geo_id) REFERENCES fdp_geo.geo_areas(geo_id);

CREATE INDEX IF NOT EXISTS idx_crime_incident_geo_id_time
  ON fdp_public_safety.crime_incident (geo_id, incident_at DESC)
  WHERE geo_id IS NOT NULL;