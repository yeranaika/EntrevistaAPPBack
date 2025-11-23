-- =============================================================================
-- Migración 009: Arreglar valores NULL en tipo_pregunta
-- =============================================================================
-- Este script actualiza registros existentes que tienen tipo_pregunta NULL
-- antes de que Exposed intente agregar la restricción NOT NULL
-- =============================================================================

BEGIN;

-- Actualizar registros NULL para preguntas de nivelación
UPDATE app.pregunta
SET tipo_pregunta = 'opcion_multiple'
WHERE tipo_banco = 'NV'
  AND tipo_pregunta IS NULL;

-- Actualizar cualquier otro registro NULL (preguntas antiguas del banco general)
UPDATE app.pregunta
SET tipo_pregunta = 'opcion_multiple'
WHERE tipo_pregunta IS NULL;

-- Verificar que no queden NULLs
SELECT
    COUNT(*) as total_registros,
    COUNT(tipo_pregunta) as con_tipo_pregunta,
    COUNT(*) - COUNT(tipo_pregunta) as nulls_restantes
FROM app.pregunta;

COMMIT;

-- =============================================================================
-- Ahora Exposed podrá agregar la restricción NOT NULL sin errores
-- =============================================================================
