-- Migración: Crear tabla de tickets de soporte
-- Fecha: 2025-11-17
-- Descripción: Sistema de tickets para reportar problemas y consultas

-- Crear tabla de tickets
CREATE TABLE IF NOT EXISTS app.ticket (
    ticket_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES app.usuario(usuario_id) ON DELETE CASCADE,
    mensaje TEXT NOT NULL,
    categoria VARCHAR(50) NOT NULL CHECK (categoria IN ('bug', 'sugerencia', 'consulta', 'otro')),
    estado VARCHAR(20) NOT NULL DEFAULT 'abierto' CHECK (estado IN ('abierto', 'en_proceso', 'resuelto', 'cerrado')),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP
);

-- Crear índices para optimizar búsquedas
CREATE INDEX IF NOT EXISTS idx_ticket_usuario ON app.ticket(usuario_id);
CREATE INDEX IF NOT EXISTS idx_ticket_estado ON app.ticket(estado);
CREATE INDEX IF NOT EXISTS idx_ticket_categoria ON app.ticket(categoria);
CREATE INDEX IF NOT EXISTS idx_ticket_fecha_creacion ON app.ticket(fecha_creacion DESC);

-- Trigger para actualizar fecha_actualizacion automáticamente
CREATE OR REPLACE FUNCTION app.update_ticket_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_ticket_timestamp
    BEFORE UPDATE ON app.ticket
    FOR EACH ROW
    EXECUTE FUNCTION app.update_ticket_timestamp();

-- Comentarios para documentación
COMMENT ON TABLE app.ticket IS 'Tickets de soporte creados por usuarios';
COMMENT ON COLUMN app.ticket.ticket_id IS 'Identificador único del ticket';
COMMENT ON COLUMN app.ticket.usuario_id IS 'Usuario que creó el ticket';
COMMENT ON COLUMN app.ticket.mensaje IS 'Descripción del problema o consulta';
COMMENT ON COLUMN app.ticket.categoria IS 'Categoría del ticket: bug, sugerencia, consulta, otro';
COMMENT ON COLUMN app.ticket.estado IS 'Estado actual: abierto, en_proceso, resuelto, cerrado';
COMMENT ON COLUMN app.ticket.fecha_creacion IS 'Fecha y hora de creación del ticket';
COMMENT ON COLUMN app.ticket.fecha_actualizacion IS 'Última actualización del ticket';
