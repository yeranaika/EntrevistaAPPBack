-- EJECUTAR ESTE SCRIPT EN DBEAVER PARA FIX RÁPIDO
-- Cambia las columnas JSON a TEXT en tabla retroalimentacion

ALTER TABLE app.retroalimentacion
ALTER COLUMN aciertos TYPE TEXT,
ALTER COLUMN faltantes TYPE TEXT;

-- Verificar cambios
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'retroalimentacion'
  AND column_name IN ('aciertos', 'faltantes');
