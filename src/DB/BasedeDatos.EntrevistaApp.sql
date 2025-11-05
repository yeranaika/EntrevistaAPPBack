-- Encabezado recomendado
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS app;
SET search_path TO app, public;

BEGIN;

-- 1) Núcleo de cuentas y perfil

CREATE TABLE usuario (
    usuario_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo VARCHAR(320) NOT NULL UNIQUE,
    contrasena_hash VARCHAR(255) NOT NULL,
    nombre VARCHAR(120),
    idioma VARCHAR(10) NOT NULL DEFAULT 'es',
    estado VARCHAR(19) NOT NULL DEFAULT 'activo',
    rol VARCHAR(10)  NOT NULL DEFAULT 'user',
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_email_format CHECK (correo ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('user','admin'))

);

-- Tabla de refresh tokens para gestión de sesiones
CREATE TABLE refresh_token (
    refresh_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_rt_exp CHECK (expires_at > issued_at)
);

CREATE TABLE perfil_usuario (
    perfil_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    nivel_experiencia VARCHAR(40),
    area VARCHAR(10),
    flags_accesibilidad JSON,
    nota_objetivos TEXT,
    pais VARCHAR(2),  -- ISO 3166-1 alpha-2
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset (
  token       UUID PRIMARY KEY,
  usuario_id  UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE, -- CORREGIDO: Era usuario(id)
  code        VARCHAR(12) NOT NULL,
  issued_at   TIMESTAMPTZ NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  used        BOOLEAN NOT NULL DEFAULT FALSE
);


CREATE TABLE consentimiento (
    consentimiento_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    version VARCHAR(20),
    alcances JSON,
    fecha_otorgado TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_revocado TIMESTAMPTZ
);


-- 2) Suscripciones y pagos

CREATE TABLE suscripcion (
    suscripcion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    plan VARCHAR(10) NOT NULL DEFAULT 'free',
    proveedor VARCHAR(20),
    estado VARCHAR(12) NOT NULL DEFAULT 'inactiva',
    fecha_inicio TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_renovacion TIMESTAMPTZ,
    fecha_expiracion TIMESTAMPTZ,
    CONSTRAINT chk_estado_suscripcion CHECK (estado IN ('activa', 'inactiva', 'cancelada', 'suspendida', 'vencida'))
);

CREATE TABLE pago (
    pago_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suscripcion_id UUID NOT NULL REFERENCES suscripcion(suscripcion_id) ON DELETE CASCADE,
    proveedor_tx_id VARCHAR(120) NOT NULL UNIQUE,
    monto_clp INT NOT NULL,
    estado VARCHAR(8) NOT NULL DEFAULT 'pendiente',
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_monto_positivo CHECK (monto_clp > 0),
    CONSTRAINT chk_estado_pago CHECK (estado IN ('pendiente', 'aprobado', 'fallido', 'reembolso'))
);


-- 3) Contenidos: objetivos/cargos y banco de preguntas

CREATE TABLE objetivo_carrera (
    objetivo_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    nombre_cargo VARCHAR(120) NOT NULL,
    sector VARCHAR(10),
    skills_enfoque JSON,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pregunta (
    pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco VARCHAR(5),  -- Códigos: "tec", "soft", "mix"
    sector VARCHAR(80),
    nivel VARCHAR(3),  -- Códigos: "jr", "mid", "sr"
    texto TEXT NOT NULL,
    pistas JSON,
    historica JSON,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- 4) Pruebas (tests) y sus intentos

CREATE TABLE prueba (
    prueba_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_prueba VARCHAR(8) NOT NULL DEFAULT 'aprendiz',
    area VARCHAR(80),
    nivel VARCHAR(3),  -- Códigos: "jr", "mid", "sr"
    metadata VARCHAR(120),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE prueba_pregunta (
    prueba_pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prueba_id UUID NOT NULL REFERENCES prueba(prueba_id) ON DELETE CASCADE,
    pregunta_id UUID NOT NULL REFERENCES pregunta(pregunta_id) ON DELETE RESTRICT,
    orden INT NOT NULL,
    opciones JSON,
    clave_correcta VARCHAR(40),
    CONSTRAINT chk_orden_positivo CHECK (orden > 0)
);

CREATE UNIQUE INDEX uq_prueba_pregunta_orden ON prueba_pregunta(prueba_id, orden);

CREATE TABLE IF NOT EXISTS public.intento_prueba (
  intento_id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  usuario_id            UUID        NOT NULL REFERENCES public.usuario(usuario_id) ON DELETE CASCADE,
  prueba_id             UUID        NOT NULL REFERENCES public.prueba(prueba_id)  ON DELETE CASCADE,

  -- tal como en tu Exposed (varchar de 50)
  fecha_inicio          VARCHAR(50) NOT NULL,
  fecha_fin             VARCHAR(50),

  -- columnas adicionales que usa tu repo
  puntaje_total         INTEGER     NOT NULL DEFAULT 0,
  estado                VARCHAR(20) NOT NULL DEFAULT 'en_progreso',
  tiempo_total_segundos INTEGER,
  creado_en             VARCHAR(50) NOT NULL,
  actualizado_en        VARCHAR(50) NOT NULL
);

-- Índices útiles
CREATE INDEX IF NOT EXISTS idx_intento_usuario_prueba ON public.intento_prueba(usuario_id, prueba_id);
CREATE INDEX IF NOT EXISTS idx_intento_fecha_inicio   ON public.intento_prueba(fecha_inicio);

CREATE TABLE respuesta_prueba (
    respuesta_prueba_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intento_id UUID NOT NULL REFERENCES intento_prueba(intento_id) ON DELETE CASCADE,
    prueba_pregunta_id UUID NOT NULL REFERENCES prueba_pregunta(prueba_pregunta_id) ON DELETE CASCADE,
    respuesta_usuario TEXT,
    correcta BOOLEAN,
    feedback_inspecl TEXT
);

CREATE UNIQUE INDEX uq_respuesta_prueba_item ON respuesta_prueba(intento_id, prueba_pregunta_id);


-- 5) Sesiones de entrevista (chat) y feedback

CREATE TABLE sesion_entrevista (
    sesion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    modo VARCHAR(5),  -- Códigos: "tec", "soft", "mix"
    nivel VARCHAR(3),  -- Códigos: "jr", "mid", "sr"
    fecha_inicio TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin TIMESTAMPTZ,
    es_premium BOOLEAN NOT NULL DEFAULT FALSE,
    puntaje_general NUMERIC(5,2),
    CONSTRAINT chk_puntaje_general CHECK (puntaje_general >= 0 AND puntaje_general <= 100)
);

CREATE INDEX sesion_entrevista_user_idx ON sesion_entrevista(usuario_id, fecha_inicio DESC);
CREATE INDEX idx_sesion_activa ON sesion_entrevista(usuario_id, fecha_inicio DESC) WHERE fecha_fin IS NULL;

CREATE TABLE sesion_pregunta (
    sesion_pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id UUID NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    pregunta_id UUID REFERENCES pregunta(pregunta_id),
    orden INT NOT NULL,
    texto_ref TEXT,
    recomendaciones TEXT,
    tiempo_entrega_ms INT,
    CONSTRAINT chk_tiempo_positivo CHECK (tiempo_entrega_ms IS NULL OR tiempo_entrega_ms > 0)
);

CREATE UNIQUE INDEX uq_sesion_pregunta_orden ON sesion_pregunta(sesion_id, orden);

CREATE TABLE respuesta (
    respuesta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_pregunta_id UUID NOT NULL REFERENCES sesion_pregunta(sesion_pregunta_id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    texto TEXT NOT NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    tokens_in INT,
    CONSTRAINT chk_tokens_positivos CHECK (tokens_in IS NULL OR tokens_in > 0)
);

CREATE UNIQUE INDEX uq_respuesta_por_pregunta ON respuesta(sesion_pregunta_id);

CREATE TABLE retroalimentacion (
    retroalimentacion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    respuesta_id UUID NOT NULL UNIQUE REFERENCES respuesta(respuesta_id) ON DELETE CASCADE,
    nivel_feedback VARCHAR(8),  -- Códigos: "free", "premium"
    enunciado TEXT,
    aciertos JSON,
    faltantes JSON
);


-- 6) Instituciones y licencias

CREATE TABLE institucion (
    institucion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(160),
    tipo VARCHAR(40),
    pais VARCHAR(2),  -- ISO 3166-1 alpha-2
    website VARCHAR(80),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE institucion_miembro (
    miembro_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institucion_id UUID NOT NULL REFERENCES institucion(institucion_id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    rol VARCHAR(20),  -- admin | docente | alumno
    estado VARCHAR(12) NOT NULL DEFAULT 'activo',
    fecha_alta TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_baja TIMESTAMPTZ,
    CONSTRAINT chk_estado_miembro CHECK (estado IN ('activo', 'inactivo', 'suspendido'))
);

CREATE TABLE licencia_institucional (
    licencia_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institucion_id UUID NOT NULL REFERENCES institucion(institucion_id) ON DELETE CASCADE,
    plan VARCHAR(20) NOT NULL,
    estado VARCHAR(12) NOT NULL DEFAULT 'activa',
    fecha_inicio TIMESTAMPTZ NOT NULL,
    fecha_fin TIMESTAMPTZ,
    seats INT,
    CONSTRAINT chk_seats_positivos CHECK (seats IS NULL OR seats > 0),
    CONSTRAINT chk_estado_licencia CHECK (estado IN ('activa', 'inactiva', 'vencida', 'suspendida'))
);

CREATE TABLE licencia_asignacion (
    asignacion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    licencia_id UUID NOT NULL REFERENCES licencia_institucional(licencia_id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    estado VARCHAR(12) NOT NULL DEFAULT 'activa',
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin TIMESTAMPTZ,
    CONSTRAINT chk_estado_asignacion CHECK (estado IN ('activa', 'inactiva', 'revocada'))
);


-- 7) Offline cache y auditoría

CREATE TABLE cache_offline (
    cache_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    dispositivo_id VARCHAR(120) NOT NULL,
    clave_contenido JSON NOT NULL,
    fecha_ultima_sync TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE log_auditoria (
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID REFERENCES usuario(usuario_id) ON DELETE SET NULL,
    tipo_evento VARCHAR(80) NOT NULL,
    origen VARCHAR(60),
    payload JSON,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ÍNDICES OPTIMIZADOS PARA PERFORMANCE


-- Índices para usuarios y perfiles
CREATE INDEX idx_perfil_usuario ON perfil_usuario(usuario_id);
CREATE INDEX idx_usuario_correo_activo ON usuario(correo) WHERE estado = 'activo';

-- Indice para recuperacion de contraseñas
CREATE INDEX IF NOT EXISTS idx_password_reset_usuario ON password_reset(usuario_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_code ON password_reset(code);
CREATE INDEX IF NOT EXISTS idx_password_reset_expires ON password_reset(expires_at);

-- Índices para consentimientos y suscripciones
CREATE INDEX idx_consentimiento_usuario ON consentimiento(usuario_id);
CREATE INDEX idx_suscripcion_usuario ON suscripcion(usuario_id);
CREATE INDEX idx_suscripcion_activa ON suscripcion(usuario_id, estado) WHERE estado = 'activa';

-- Índices para contenidos
CREATE INDEX idx_objetivo_usuario ON objetivo_carrera(usuario_id);
CREATE INDEX idx_pregunta_activa ON pregunta(nivel, tipo_banco) WHERE activa = TRUE;
CREATE INDEX idx_prueba_activa ON prueba(tipo_prueba, nivel) WHERE activo = TRUE;

-- Índices para instituciones
CREATE INDEX idx_institucion_miembro_inst ON institucion_miembro(institucion_id);
CREATE INDEX idx_institucion_miembro_user ON institucion_miembro(usuario_id);
CREATE INDEX idx_licencia_inst ON licencia_institucional(institucion_id);

-- Índices para cache y auditoría
CREATE INDEX idx_cache_usuario ON cache_offline(usuario_id);
CREATE INDEX idx_cache_dispositivo ON cache_offline(dispositivo_id);
CREATE INDEX idx_log_usuario ON log_auditoria(usuario_id, fecha_creacion DESC);
CREATE INDEX idx_log_tipo ON log_auditoria(tipo_evento, fecha_creacion DESC);

-- Índices para refresh tokens
CREATE INDEX  idx_refresh_usuario ON refresh_token(usuario_id);
-- CORREGIDO: Se eliminó la línea 'CREATE INDEX  idx_refresh_validos' que estaba incompleta.


-- COMENTARIOS DE DOCUMENTACIÓN


COMMENT ON TABLE usuario IS 'Tabla principal de usuarios del sistema';
COMMENT ON TABLE perfil_usuario IS 'Información extendida del perfil de usuario';
COMMENT ON TABLE sesion_entrevista IS 'Sesiones de entrevista simulada con IA';
COMMENT ON TABLE retroalimentacion IS 'Feedback generado por IA para respuestas';
COMMENT ON TABLE institucion IS 'Instituciones educativas o empresariales';
COMMENT ON TABLE licencia_institucional IS 'Licencias por volumen para instituciones';

COMMENT ON COLUMN perfil_usuario.pais IS 'Código ISO 3166-1 alpha-2 (Ej: CL, US, AR)';
COMMENT ON COLUMN pregunta.nivel IS 'Códigos: jr=junior, mid=intermedio, sr=senior';
COMMENT ON COLUMN sesion_entrevista.modo IS 'Códigos: tec=técnica, soft=habilidades blandas, mix=mixto';

-- Finaliza la transacción y guarda todos los cambios
COMMIT;
