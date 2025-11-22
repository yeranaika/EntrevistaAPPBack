CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE pregunta (
    pregunta_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco       VARCHAR(5),
    sector           VARCHAR(80),
    nivel            VARCHAR(4),
    texto            TEXT NOT NULL,
    pistas           JSONB,
    config_respuesta JSONB,
    activa           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now()
);
