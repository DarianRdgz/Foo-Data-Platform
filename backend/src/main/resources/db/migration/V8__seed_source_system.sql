-- V8__seed_source_system.sql
-- Seeds the initial source_system rows required by SourceSystemService at runtime
-- Add future sources here with additional INSERT rows

INSERT INTO fdp_core.source_system (code, display_name)
VALUES ('KROGER', 'Kroger')
ON CONFLICT (code) DO NOTHING;
