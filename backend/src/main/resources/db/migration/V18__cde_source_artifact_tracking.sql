CREATE TABLE IF NOT EXISTS fdp_core.source_artifact (
  id                UUID PRIMARY KEY,
  source_system_id  SMALLINT NOT NULL REFERENCES fdp_core.source_system(id),
  artifact_type     VARCHAR(40)  NOT NULL,
  collection_code   VARCHAR(120) NOT NULL,
  state_code        VARCHAR(40),
  artifact_year     INTEGER,
  original_filename VARCHAR(255) NOT NULL,
  storage_path      TEXT NOT NULL,
  source_url        TEXT,
  sha256            CHAR(64) NOT NULL,
  file_size_bytes   BIGINT NOT NULL,
  status            VARCHAR(20) NOT NULL,
  notes             TEXT,
  downloaded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  processed_at      TIMESTAMPTZ,
  ingestion_run_id  UUID REFERENCES fdp_core.ingestion_run(id),
  CONSTRAINT uq_source_artifact_source_sha UNIQUE (source_system_id, sha256),
  CONSTRAINT ck_source_artifact_status
      CHECK (status IN ('DOWNLOADED', 'INGESTED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_source_artifact_source_status_downloaded
  ON fdp_core.source_artifact (source_system_id, status, downloaded_at DESC);
