CREATE TABLE IF NOT EXISTS ingestion_run (
  id           BIGSERIAL PRIMARY KEY,
  source       VARCHAR(64) NOT NULL, 
  started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at  TIMESTAMPTZ,
  status       VARCHAR(20) NOT NULL,         -- RUNNING / SUCCESS / FAILED
  message      TEXT
);

CREATE INDEX IF NOT EXISTS idx_ingestion_run_source_started
  ON ingestion_run(source, started_at DESC);
