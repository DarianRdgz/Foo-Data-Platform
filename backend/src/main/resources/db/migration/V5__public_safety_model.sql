-- V5__public_safety_model.sql
CREATE TABLE IF NOT EXISTS fdp_public_safety.crime_incident (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system_id     SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  source_incident_id   TEXT NOT NULL,
  geo_region_id        BIGINT REFERENCES fdp_core.geo_region(id),
  incident_at          TIMESTAMPTZ NOT NULL,
  offense_code         TEXT,
  offense_name         TEXT,
  offense_description  TEXT,
  crime_against        TEXT,
  action_code          TEXT,
  source_original_type TEXT,
  incident_source_name TEXT,
  latitude             NUMERIC(9,6),
  longitude            NUMERIC(9,6),
  address              TEXT,
  ingestion_run_id     UUID REFERENCES fdp_core.ingestion_run(id),
  raw_payload_id       UUID REFERENCES fdp_core.raw_payload(id),
  UNIQUE (source_system_id, source_incident_id)
);
CREATE INDEX IF NOT EXISTS idx_crime_incident_time
  ON fdp_public_safety.crime_incident (incident_at DESC);
CREATE INDEX IF NOT EXISTS idx_crime_incident_geo_time
  ON fdp_public_safety.crime_incident (geo_region_id, incident_at DESC);
