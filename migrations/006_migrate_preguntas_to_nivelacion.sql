-- =============================================================================
-- Migración: Convertir preguntas de 'pregunta' a 'pregunta_nivelacion'
-- =============================================================================
-- Este script convierte las preguntas existentes en la tabla 'pregunta'
-- al formato requerido por 'pregunta_nivelacion'
--
-- Conversiones:
-- - sector → habilidad
-- - nivel (jr/mid/sr) → dificultad (1/2/3)
-- - config_respuesta.opciones → opciones (JSON array de textos)
-- - config_respuesta.respuesta_correcta (A/B/C) → respuesta_correcta (0/1/2)
-- =============================================================================

BEGIN;

-- Crear tabla pregunta_nivelacion si no existe
CREATE TABLE IF NOT EXISTS app.pregunta_nivelacion (
    pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    habilidad VARCHAR(50) NOT NULL,
    dificultad INTEGER NOT NULL DEFAULT 1,
    enunciado TEXT NOT NULL,
    opciones TEXT NOT NULL,
    respuesta_correcta INTEGER NOT NULL,
    explicacion TEXT,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_dificultad CHECK (dificultad IN (1, 2, 3))
);

-- Insertar preguntas de selección única (opción múltiple)
INSERT INTO app.pregunta_nivelacion (
    habilidad,
    dificultad,
    enunciado,
    opciones,
    respuesta_correcta,
    explicacion,
    activa,
    fecha_creacion
)
SELECT 
    p.sector AS habilidad,
    CASE 
        WHEN p.nivel = 'jr' THEN 1
        WHEN p.nivel = 'mid' THEN 2
        WHEN p.nivel = 'sr' THEN 3
        ELSE 1
    END AS dificultad,
    p.texto AS enunciado,
    -- Convertir opciones de JSONB a JSON array de strings
    (
        SELECT json_agg(opt->>'texto')::text
        FROM jsonb_array_elements(p.config_respuesta->'opciones') AS opt
    ) AS opciones,
    -- Convertir respuesta correcta de letra (A/B/C) a índice (0/1/2)
    CASE p.config_respuesta->>'respuesta_correcta'
        WHEN 'A' THEN 0
        WHEN 'B' THEN 1
        WHEN 'C' THEN 2
        ELSE 0
    END AS respuesta_correcta,
    -- Usar las pistas como explicación (concatenadas)
    (
        SELECT string_agg(pista::text, ' - ')
        FROM jsonb_array_elements_text(p.pistas) AS pista
    ) AS explicacion,
    p.activa,
    p.fecha_creacion
FROM app.pregunta p
WHERE p.tipo_banco = 'PR'
  AND p.config_respuesta->>'tipo' = 'seleccion_unica'
  AND p.activa = TRUE;

-- Verificar resultados
SELECT 
    habilidad,
    dificultad,
    COUNT(*) as total_preguntas
FROM app.pregunta_nivelacion
GROUP BY habilidad, dificultad
ORDER BY habilidad, dificultad;

COMMIT;

-- =============================================================================
-- Resumen de la migración:
-- - Preguntas de tipo 'seleccion_unica' migradas a pregunta_nivelacion
-- - Preguntas de tipo 'abierta_texto' NO migradas (requieren evaluación manual)
-- - Total esperado: ~80 preguntas de opción múltiple
-- =============================================================================
