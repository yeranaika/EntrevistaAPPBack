-- Migration: Add historica column to prueba table
-- Purpose: Store JSON of historical changes for prueba records

-- Set search path to include app schema
SET search_path TO app, public;

ALTER TABLE app.prueba
ADD COLUMN IF NOT EXISTS historica TEXT NULL;

COMMENT ON COLUMN app.prueba.historica IS 'JSON storage for historical changes to prueba records';
