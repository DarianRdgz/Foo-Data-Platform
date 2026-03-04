ALTER TABLE fdp_core.ingestion_run
  ADD COLUMN IF NOT EXISTS records_written INTEGER NOT NULL DEFAULT 0;

ALTER TABLE fdp_core.ingestion_run
  ADD COLUMN IF NOT EXISTS error_detail TEXT;

CREATE INDEX IF NOT EXISTS idx_ingestion_run_started_desc
  ON fdp_core.ingestion_run (started_at DESC);

CREATE INDEX IF NOT EXISTS idx_ingestion_run_source_status_started_desc
  ON fdp_core.ingestion_run (source_system_id, status, started_at DESC);