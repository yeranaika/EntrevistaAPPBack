-- =============================================================================
-- Migración 008: Agregar columnas para sistema de nivelación
-- =============================================================================
-- Agrega las columnas 'puntaje' y 'recomendaciones' a la tabla intento_prueba
-- para soportar el sistema de evaluación de tests de nivelación
-- =============================================================================

BEGIN;

-- Agregar columna puntaje (decimal) si no existe
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
        AND table_name = 'intento_prueba'
        AND column_name = 'puntaje'
    ) THEN
        ALTER TABLE app.intento_prueba
        ADD COLUMN puntaje DECIMAL(5,2);
    END IF;
END $$;

-- Agregar columna recomendaciones (text) si no existe
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app'
        AND table_name = 'intento_prueba'
        AND column_name = 'recomendaciones'
    ) THEN
        ALTER TABLE app.intento_prueba
        ADD COLUMN recomendaciones TEXT;
    END IF;
END $$;

-- Verificar las columnas agregadas
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'intento_prueba'
  AND column_name IN ('puntaje', 'recomendaciones')
ORDER BY column_name;

COMMIT;

-- =============================================================================
-- NOTA: Estas columnas son necesarias para el sistema de tests de nivelación
-- - puntaje: Almacena el puntaje decimal (0.00 - 100.00) del test
-- - recomendaciones: Almacena el feedback generado basado en el resultado
-- =============================================================================
