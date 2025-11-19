-- Migración: Cambiar columnas JSON a TEXT en tabla retroalimentacion
-- Fecha: 2025-11-19
-- Descripción: Soluciona el error "column is of type json but expression is of type character varying"
--              cambiando las columnas aciertos y faltantes de JSON a TEXT.

BEGIN;

-- Cambiar tipo de columna aciertos de JSON a TEXT
ALTER TABLE app.retroalimentacion
ALTER COLUMN aciertos TYPE TEXT;

-- Cambiar tipo de columna faltantes de JSON a TEXT
ALTER TABLE app.retroalimentacion
ALTER COLUMN faltantes TYPE TEXT;

-- Agregar comentario explicativo
COMMENT ON COLUMN app.retroalimentacion.aciertos IS
'Puntos positivos de la respuesta (JSON serializado como TEXT)';

COMMENT ON COLUMN app.retroalimentacion.faltantes IS
'Áreas de mejora de la respuesta (JSON serializado como TEXT)';

COMMIT;

-- Verificación post-migración
-- SELECT column_name, data_type
-- FROM information_schema.columns
-- WHERE table_schema = 'app'
--   AND table_name = 'retroalimentacion'
--   AND column_name IN ('aciertos', 'faltantes');
