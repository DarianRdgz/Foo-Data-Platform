-- V3__core_ingestion_and_ops.sql
CREATE TABLE IF NOT EXISTS fdp_core.ingestion_run (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system_id     SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  started_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at          TIMESTAMPTZ,
  status               TEXT NOT NULL DEFAULT 'RUNNING',
  message              TEXT,
  requested_scope_json JSONB,
  locked_at            TIMESTAMPTZ,
  locked_by            TEXT
);
CREATE INDEX IF NOT EXISTS idx_ingestion_run_source_started
  ON fdp_core.ingestion_run (source_system_id, started_at DESC);

CREATE TABLE IF NOT EXISTS fdp_core.raw_payload (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  source_system_id SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  endpoint         TEXT NOT NULL,
  requested_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  response_status  INT,
  payload_json     JSONB NOT NULL,
  ingestion_run_id UUID REFERENCES fdp_core.ingestion_run(id)
);
CREATE INDEX IF NOT EXISTS idx_raw_payload_source_time
  ON fdp_core.raw_payload (source_system_id, requested_at DESC);

CREATE TABLE IF NOT EXISTS fdp_core.ingestion_lock (
  source_system_id SMALLINT PRIMARY KEY REFERENCES fdp_core.source_system(id),
  locked_at        TIMESTAMPTZ NOT NULL,
  locked_by        TEXT NOT NULL,
  expires_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_ingestion_lock_expires
  ON fdp_core.ingestion_lock (expires_at);

CREATE TABLE IF NOT EXISTS fdp_core.api_quota_usage (
  source_system_id SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  usage_date       DATE NOT NULL,
  endpoint         TEXT NOT NULL,
  call_count       INTEGER NOT NULL DEFAULT 0,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (source_system_id, usage_date, endpoint)
);
CREATE INDEX IF NOT EXISTS idx_api_quota_usage_date
  ON fdp_core.api_quota_usage (usage_date);
