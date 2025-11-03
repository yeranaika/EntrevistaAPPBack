-- ============================================
-- TABLAS PARA INTENTOS DE PRUEBA Y RESPUESTAS
-- ============================================

-- 1) Tabla de intentos de prueba
CREATE TABLE intento_prueba (
    intento_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    prueba_id UUID NOT NULL REFERENCES prueba(prueba_id) ON DELETE CASCADE,
    fecha_inicio TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin TIMESTAMPTZ,
    puntaje_total INT DEFAULT 0,
    estado VARCHAR(20) NOT NULL DEFAULT 'en_progreso', -- 'en_progreso', 'finalizado', 'abandonado'
    tiempo_total_segundos INT,
    creado_en TIMESTAMPTZ DEFAULT now(),
    actualizado_en TIMESTAMPTZ DEFAULT now()
);

-- 2) Tabla de respuestas del usuario
CREATE TABLE respuesta_prueba (
    respuesta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intento_id UUID NOT NULL REFERENCES intento_prueba(intento_id) ON DELETE CASCADE,
    pregunta_id UUID NOT NULL REFERENCES pregunta(pregunta_id) ON DELETE CASCADE,
    respuesta_usuario TEXT NOT NULL,
    es_correcta BOOLEAN,
    puntaje_obtenido INT DEFAULT 0,
    tiempo_respuesta_segundos INT,
    orden INT NOT NULL, -- orden en que se respondió
    creado_en TIMESTAMPTZ DEFAULT now(),
    UNIQUE(intento_id, pregunta_id) -- No responder 2 veces la misma pregunta
);

-- 3) Tabla para tracking de preguntas mostradas (opcional pero útil)
CREATE TABLE pregunta_mostrada (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intento_id UUID NOT NULL REFERENCES intento_prueba(intento_id) ON DELETE CASCADE,
    pregunta_id UUID NOT NULL REFERENCES pregunta(pregunta_id) ON DELETE CASCADE,
    orden INT NOT NULL,
    mostrada_en TIMESTAMPTZ DEFAULT now(),
    UNIQUE(intento_id, pregunta_id)
);

-- Índices para mejorar rendimiento
CREATE INDEX idx_intento_usuario ON intento_prueba(usuario_id);
CREATE INDEX idx_intento_prueba ON intento_prueba(prueba_id);
CREATE INDEX idx_intento_estado ON intento_prueba(estado);
CREATE INDEX idx_respuesta_intento ON respuesta_prueba(intento_id);
CREATE INDEX idx_respuesta_pregunta ON respuesta_prueba(pregunta_id);

-- Trigger para actualizar fecha de actualización
CREATE OR REPLACE FUNCTION actualizar_fecha_modificacion()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_intento_actualizado
    BEFORE UPDATE ON intento_prueba
    FOR EACH ROW
    EXECUTE FUNCTION actualizar_fecha_modificacion();

-- Vista útil para ver estadísticas de intentos
CREATE VIEW v_estadisticas_intento AS
SELECT 
    i.intento_id,
    i.usuario_id,
    u.nombre as usuario_nombre,
    i.prueba_id,
    p.titulo as prueba_titulo,
    i.fecha_inicio,
    i.fecha_fin,
    i.puntaje_total,
    i.estado,
    i.tiempo_total_segundos,
    COUNT(r.respuesta_id) as total_respuestas,
    SUM(CASE WHEN r.es_correcta THEN 1 ELSE 0 END) as respuestas_correctas,
    ROUND(
        (SUM(CASE WHEN r.es_correcta THEN 1 ELSE 0 END)::DECIMAL / 
         NULLIF(COUNT(r.respuesta_id), 0)) * 100, 
        2
    ) as porcentaje_aciertos
FROM intento_prueba i
LEFT JOIN usuario u ON i.usuario_id = u.usuario_id
LEFT JOIN prueba p ON i.prueba_id = p.prueba_id
LEFT JOIN respuesta_prueba r ON i.intento_id = r.intento_id
GROUP BY i.intento_id, u.nombre, p.titulo;

-- Comentarios para documentación
COMMENT ON TABLE intento_prueba IS 'Almacena cada intento de prueba que hace un usuario';
COMMENT ON TABLE respuesta_prueba IS 'Almacena las respuestas individuales dentro de un intento';
COMMENT ON COLUMN intento_prueba.estado IS 'Estados: en_progreso, finalizado, abandonado';
COMMENT ON COLUMN respuesta_prueba.es_correcta IS 'NULL si no se ha calificado aún';