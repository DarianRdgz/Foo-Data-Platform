-- Seed source system rows required by IngestionRunService.startRun() at runtime.
-- DISASTER_RISK is used by DisasterRiskScoreService, not an external feed.
INSERT INTO fdp_core.source_system (code, display_name)
VALUES
  ('COLLEGE_SCORECARD', 'U.S. Department of Education College Scorecard'),
  ('FEMA',              'FEMA OpenFEMA Disaster Declarations'),
  ('NOAA',              'NOAA Storm Events Database'),
  ('DISASTER_RISK',     'FDP Disaster Risk Score Engine')
ON CONFLICT (code) DO NOTHING;