-- V13__seed_zillow_source_system.sql

INSERT INTO fdp_core.source_system (code, display_name)
VALUES ('ZILLOW', 'Zillow')
ON CONFLICT (code) DO NOTHING;