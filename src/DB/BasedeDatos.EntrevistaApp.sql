-- =============================================================================
-- 0. LIMPIEZA Y CONFIGURACIÓN INICIAL
-- =============================================================================
-- Borramos el esquema completo para empezar de cero (CUIDADO: Borra datos previos)
DROP SCHEMA IF EXISTS app CASCADE;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS app;
SET search_path TO app, public;

BEGIN;

-- =============================================================================
-- 1. CREACIÓN DE TABLAS (DDL)
-- =============================================================================

-- 1) Núcleo de cuentas y seguridad
CREATE TABLE usuario (
    usuario_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo           VARCHAR(320) NOT NULL UNIQUE,
    contrasena_hash  VARCHAR(255) NOT NULL,
    nombre           VARCHAR(120),

    -- Preferencias / estado de la cuenta
    idioma           VARCHAR(10)  NOT NULL DEFAULT 'es',
    estado           VARCHAR(19)  NOT NULL DEFAULT 'activo',
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rol              VARCHAR(10)  NOT NULL DEFAULT 'user',

    -- Datos de perfil / métricas
    telefono           VARCHAR(20),
    origen_registro    VARCHAR(20)  NOT NULL DEFAULT 'local',   -- local / google / otros
    fecha_ultimo_login TIMESTAMPTZ,
    fecha_nacimiento   DATE,
    genero             VARCHAR(20),

    -- Constraints
    CONSTRAINT chk_email_format CHECK (correo ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('user','admin')),
    CONSTRAINT chk_usuario_origen_registro CHECK (origen_registro IN ('local','google','otros')),
    CONSTRAINT chk_usuario_telefono CHECK (telefono IS NULL OR telefono ~ '^\+?[0-9]{7,20}$'),
    
    CONSTRAINT chk_usuario_fecha_nacimiento CHECK (
            fecha_nacimiento IS NULL OR(
            fecha_nacimiento >= DATE '1900-01-01'
            AND fecha_nacimiento <= (CURRENT_DATE - INTERVAL '14 years'))
    ),

    CONSTRAINT chk_usuario_genero CHECK (genero IS NULL OR genero IN (
            'masculino',
            'femenino',
            'no_binario',
            'otro',
            'prefiere_no_decirlo'
        )
    )
);


CREATE TABLE refresh_token (
    refresh_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_rt_exp CHECK (expires_at > issued_at)
);

CREATE TABLE perfil_usuario (
    perfil_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    nivel_experiencia   VARCHAR(40),
    area                VARCHAR(50),
    flags_accesibilidad JSON,
    nota_objetivos      TEXT,
    pais                VARCHAR(2),
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE oauth_account (
    oauth_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider       TEXT NOT NULL CHECK (provider = 'google'),
    subject        TEXT NOT NULL,
    email          VARCHAR(320),
    email_verified BOOLEAN,
    usuario_id     UUID REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE TABLE password_reset (
    token       UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    code        VARCHAR(12) NOT NULL,
    issued_at   TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE consentimiento (
    consentimiento_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id        UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    version           VARCHAR(20) NOT NULL,
    alcances          JSONB       NOT NULL,
    fecha_otorgado    TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_revocado    TIMESTAMPTZ
);

CREATE TABLE consentimiento_texto (
    version           VARCHAR(20) PRIMARY KEY,
    titulo            TEXT        NOT NULL,
    cuerpo            TEXT        NOT NULL,
    fecha_publicacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    vigente           BOOLEAN     NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_consentimiento_texto_vigente ON consentimiento_texto (vigente);

CREATE TABLE codigo_suscripcion (
    codigo_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    codigo           VARCHAR(32)  NOT NULL UNIQUE,  -- ej: 'PREM-ABC123XYZ'
    label            VARCHAR(80),
    duracion_dias    INTEGER      NOT NULL,         -- días que suma a la suscripción
    max_usos         INTEGER      NOT NULL DEFAULT 1,
    usos_realizados  INTEGER      NOT NULL DEFAULT 0,
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_expiracion TIMESTAMPTZ,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE
);


-- 2) Suscripciones y pagos
CREATE TABLE suscripcion (
    suscripcion_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    plan             VARCHAR(100) NOT NULL DEFAULT 'free',
    proveedor        VARCHAR(50),
    estado           VARCHAR(20) NOT NULL DEFAULT 'inactiva',
    fecha_inicio     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_renovacion TIMESTAMPTZ,
    fecha_expiracion TIMESTAMPTZ,
    codigo_id        UUID NULL,  -- 👈 FK opcional al código

    CONSTRAINT chk_estado_suscripcion
        CHECK (estado IN ('activa','inactiva','cancelada','suspendida','vencida')),

    CONSTRAINT fk_suscripcion_codigo
        FOREIGN KEY (codigo_id) REFERENCES codigo_suscripcion(codigo_id)
);



CREATE TABLE pago (
    pago_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suscripcion_id  UUID NOT NULL REFERENCES suscripcion(suscripcion_id) ON DELETE CASCADE,
    proveedor_tx_id VARCHAR(120) NOT NULL UNIQUE,
    monto_clp       INT NOT NULL,
    estado          VARCHAR(8)  NOT NULL DEFAULT 'pendiente',
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_monto_positivo CHECK (monto_clp > 0),
    CONSTRAINT chk_estado_pago CHECK (estado IN ('pendiente','aprobado','fallido','reembolso'))
);

-- Tabla principal del plan de práctica por usuario
CREATE TABLE plan_practica (
    plan_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID NOT NULL REFERENCES app.usuario(usuario_id) ON DELETE CASCADE,
    area           VARCHAR(10),
    meta_cargo     VARCHAR(120),
    nivel          VARCHAR(20),
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    activo         BOOLEAN NOT NULL DEFAULT TRUE
);

-- Detalle del plan: pasos / módulos
CREATE TABLE plan_practica_paso (
    paso_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             UUID NOT NULL REFERENCES plan_practica(plan_id) ON DELETE CASCADE,
    orden               INT NOT NULL,
    titulo              TEXT NOT NULL,
    descripcion         TEXT,
    sesiones_por_semana INT,
    CONSTRAINT chk_orden_positivo CHECK (orden > 0),
    CONSTRAINT uq_plan_paso_orden UNIQUE (plan_id, orden)
);


CREATE TABLE recordatorio_preferencia (
    usuario_id UUID PRIMARY KEY REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    dias_semana VARCHAR(50) NOT NULL,
    hora        VARCHAR(5)  NOT NULL,
    tipo_practica VARCHAR(32) NOT NULL,
    habilitado  BOOLEAN NOT NULL DEFAULT TRUE
);

-- 3) Contenidos: objetivos/cargos y banco de preguntas
CREATE TABLE objetivo_carrera (
    objetivo_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    nombre_cargo   VARCHAR(120) NOT NULL,
    sector         VARCHAR(50),
    skills_enfoque JSON,
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skills_cargo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cargo VARCHAR(120) NOT NULL,
    tipo VARCHAR(10) NOT NULL CHECK (tipo IN ('tecnico','blando')),
    descripcion TEXT NOT NULL
);


CREATE TABLE pregunta (
    pregunta_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco       VARCHAR(5),      -- 'NV' (nivelación), 'PR' (práctica), etc.
    sector           VARCHAR(80),     -- área: 'TI', 'Administracion', etc.
    nivel            VARCHAR(3),      -- 'jr', 'ssr', 'sr', o '1','2','3' en NV
    meta_cargo       VARCHAR(120),    -- cargo objetivo (opcional)
    tipo_pregunta    VARCHAR(20) NOT NULL DEFAULT 'opcion_multiple'
                     CHECK (tipo_pregunta IN ('opcion_multiple','abierta')),
    texto            TEXT NOT NULL,   -- enunciado
    pistas           JSONB,           -- hints / tags / explicaciones extra
    config_respuesta JSONB,           -- opciones y/o criterios de corrección
    activa           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now()
);


CREATE TABLE app.recovery_code (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES app.usuario(usuario_id) ON DELETE CASCADE,
    codigo          VARCHAR(6) NOT NULL,          -- 6 dígitos
    fecha_expiracion TIMESTAMPTZ NOT NULL,
    usado           BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4) Pruebas y relaciones
CREATE TABLE prueba (
    prueba_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_prueba VARCHAR(8)  NOT NULL DEFAULT 'aprendiz',
    area        VARCHAR(80),
    nivel       VARCHAR(3),
    metadata    VARCHAR(300),
    activo      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE prueba_pregunta (
    prueba_pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prueba_id          UUID NOT NULL REFERENCES prueba(prueba_id)    ON DELETE CASCADE,
    pregunta_id        UUID NOT NULL REFERENCES pregunta(pregunta_id) ON DELETE RESTRICT,
    orden              INT  NOT NULL,
    opciones           JSON,
    clave_correcta     VARCHAR(40),
    CONSTRAINT chk_orden_positivo CHECK (orden > 0)
);
CREATE UNIQUE INDEX uq_prueba_pregunta_orden ON prueba_pregunta(prueba_id, orden);

-- 5) Intentos, respuestas, sesiones y feedback
CREATE TABLE intento_prueba (
    intento_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prueba_id             UUID NOT NULL REFERENCES prueba(prueba_id)   ON DELETE CASCADE,
    usuario_id            UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    fecha_inicio          VARCHAR(50) NOT NULL DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF'),
    fecha_fin             VARCHAR(50),
    puntaje               NUMERIC(5,2),
    recomendaciones       TEXT,
    puntaje_total         INTEGER NOT NULL DEFAULT 0,
    estado                VARCHAR(20) NOT NULL DEFAULT 'en_progreso',
    tiempo_total_segundos INTEGER,
    creado_en             VARCHAR(50) NOT NULL DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF'),
    actualizado_en        VARCHAR(50) NOT NULL DEFAULT to_char(now(), 'YYYY-MM-DD"T"HH24:MI:SSOF'),
    CONSTRAINT chk_puntaje_rango CHECK (puntaje >= 0 AND puntaje <= 100)
);
CREATE INDEX intento_prueba_user_idx ON intento_prueba(usuario_id, prueba_id);

CREATE TABLE respuesta_prueba (
    respuesta_prueba_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intento_id          UUID NOT NULL REFERENCES intento_prueba(intento_id)       ON DELETE CASCADE,
    prueba_pregunta_id  UUID NOT NULL REFERENCES prueba_pregunta(prueba_pregunta_id) ON DELETE CASCADE,
    respuesta_usuario   TEXT,
    correcta            BOOLEAN,
    feedback_inspecl    TEXT
);
CREATE UNIQUE INDEX uq_respuesta_prueba_item ON respuesta_prueba(intento_id, prueba_pregunta_id);

CREATE TABLE sesion_entrevista (
    sesion_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    modo            VARCHAR(5),
    nivel           VARCHAR(3),
    fecha_inicio    TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin       TIMESTAMPTZ,
    es_premium      BOOLEAN NOT NULL DEFAULT FALSE,
    puntaje_general NUMERIC(5,2),
    CONSTRAINT chk_puntaje_general CHECK (puntaje_general >= 0 AND puntaje_general <= 100)
);
CREATE INDEX sesion_entrevista_user_idx ON sesion_entrevista(usuario_id, fecha_inicio DESC);
CREATE INDEX idx_sesion_activa ON sesion_entrevista(usuario_id, fecha_inicio DESC) WHERE fecha_fin IS NULL;

CREATE TABLE sesion_pregunta (
    sesion_pregunta_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id          UUID NOT NULL REFERENCES sesion_entrevista(sesion_id) ON DELETE CASCADE,
    pregunta_id        UUID REFERENCES pregunta(pregunta_id),
    orden              INT NOT NULL,
    texto_ref          TEXT,
    recomendaciones    TEXT,
    tiempo_entrega_ms  INT,
    CONSTRAINT chk_tiempo_positivo CHECK (tiempo_entrega_ms IS NULL OR tiempo_entrega_ms > 0)
);
CREATE UNIQUE INDEX uq_sesion_pregunta_orden ON sesion_pregunta(sesion_id, orden);

CREATE TABLE respuesta (
    respuesta_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_pregunta_id UUID NOT NULL REFERENCES sesion_pregunta(sesion_pregunta_id) ON DELETE CASCADE,
    usuario_id         UUID NOT NULL REFERENCES usuario(usuario_id)                 ON DELETE CASCADE,
    texto              TEXT NOT NULL,
    fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT now(),
    tokens_in          INT,
    CONSTRAINT chk_tokens_positivos CHECK (tokens_in IS NULL OR tokens_in > 0)
);
CREATE UNIQUE INDEX uq_respuesta_por_pregunta ON respuesta(sesion_pregunta_id);

CREATE TABLE retroalimentacion (
    retroalimentacion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    respuesta_id         UUID NOT NULL UNIQUE REFERENCES respuesta(respuesta_id) ON DELETE CASCADE,
    nivel_feedback       VARCHAR(8),
    enunciado            TEXT,
    aciertos             JSON,
    faltantes            JSON
);

-- 6) Instituciones y licencias
CREATE TABLE institucion (
    institucion_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre         VARCHAR(160),
    tipo           VARCHAR(40),
    pais           VARCHAR(2),
    website        VARCHAR(80),
    activa         BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE institucion_miembro (
    miembro_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institucion_id UUID NOT NULL REFERENCES institucion(institucion_id) ON DELETE CASCADE,
    usuario_id     UUID NOT NULL REFERENCES usuario(usuario_id)         ON DELETE CASCADE,
    rol            VARCHAR(20),
    estado         VARCHAR(12) NOT NULL DEFAULT 'activo',
    fecha_alta     TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_baja     TIMESTAMPTZ,
    CONSTRAINT chk_estado_miembro CHECK (estado IN ('activo','inactivo','suspendido'))
);

CREATE TABLE licencia_institucional (
    licencia_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    institucion_id UUID NOT NULL REFERENCES institucion(institucion_id) ON DELETE CASCADE,
    plan           VARCHAR(20) NOT NULL,
    estado         VARCHAR(12) NOT NULL DEFAULT 'activa',
    fecha_inicio   TIMESTAMPTZ NOT NULL,
    fecha_fin      TIMESTAMPTZ,
    seats          INT,
    CONSTRAINT chk_estado_licencia CHECK (estado IN ('activa','inactiva','vencida','suspendida')),
    CONSTRAINT chk_seats_positivos CHECK (seats IS NULL OR seats > 0)
);

CREATE TABLE licencia_asignacion (
    asignacion_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    licencia_id    UUID NOT NULL REFERENCES licencia_institucional(licencia_id) ON DELETE CASCADE,
    usuario_id     UUID NOT NULL REFERENCES usuario(usuario_id)                 ON DELETE CASCADE,
    estado         VARCHAR(12) NOT NULL DEFAULT 'activa',
    fecha_asignacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_fin      TIMESTAMPTZ,
    CONSTRAINT chk_estado_asignacion CHECK (estado IN ('activa','inactiva','revocada'))
);

-- 7) Cache offline y auditoría
CREATE TABLE cache_offline (
    cache_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    dispositivo_id   VARCHAR(120) NOT NULL,
    clave_contenido  JSON NOT NULL,
    fecha_ultima_sync TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cache_usuario ON cache_offline(usuario_id);
CREATE INDEX idx_cache_dispositivo ON cache_offline(dispositivo_id);

CREATE TABLE log_auditoria (
    log_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id     UUID REFERENCES usuario(usuario_id) ON DELETE SET NULL,
    tipo_evento    VARCHAR(80) NOT NULL,
    origen         VARCHAR(60),
    payload        JSON,
    fecha_creacion TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_log_usuario ON log_auditoria(usuario_id, fecha_creacion DESC);
CREATE INDEX idx_log_tipo    ON log_auditoria(tipo_evento, fecha_creacion DESC);

-- 8) Índices extra
CREATE INDEX idx_consentimiento_usuario ON consentimiento(usuario_id);
CREATE INDEX idx_perfil_usuario         ON perfil_usuario(usuario_id);
CREATE INDEX idx_objetivo_usuario ON objetivo_carrera(usuario_id);
CREATE INDEX idx_pregunta_activa  ON pregunta(nivel, tipo_banco) WHERE activa = TRUE;
CREATE INDEX idx_prueba_activa ON prueba(tipo_prueba, nivel) WHERE activo = TRUE;
CREATE INDEX idx_refresh_usuario ON refresh_token(usuario_id);
CREATE INDEX idx_suscripcion_usuario ON suscripcion(usuario_id);
CREATE INDEX idx_suscripcion_activa  ON suscripcion(usuario_id, estado) WHERE estado = 'activa';
CREATE INDEX idx_usuario_correo_activo ON usuario(correo) WHERE estado = 'activo';

COMMIT;

-- =============================================================================
-- 2. CARGA DE DATOS (INSERTS) - TEXTOS CORREGIDOS
-- =============================================================================

BEGIN;

-- 1. ANALISTA TI (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un Requisito Funcional?',
 '["Describe lo que el sistema debe hacer", "Comportamiento"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Cómo se ve el sistema"},{"id":"B", "texto":"Una función o servicio que el sistema debe proveer"},{"id":"C", "texto":"La velocidad del sistema"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 'En un diagrama de flujo, ¿qué forma representa una decisión?',
 '["Tiene forma de diamante", "Suelen salir flechas de SI/NO"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Rectángulo"},{"id":"B", "texto":"Rombo/Diamante"},{"id":"C", "texto":"Círculo"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué significan las siglas UML?',
 '["Lenguaje visual estándar", "Unified..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Universal Modeling List"},{"id":"B", "texto":"Unified Modeling Language"},{"id":"C", "texto":"User Management Logic"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Define brevemente qué es un "Stakeholder".',
 '["Interesado", "Puede afectar o ser afectado"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Cuál es el actor principal en un Caso de Uso de "Login"?',
 '["Quien inicia la acción", "Persona frente al PC"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Usuario"},{"id":"B", "texto":"Base de Datos"},{"id":"C", "texto":"Servidor"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Diferencia principal entre Requisito Funcional y No Funcional.',
 '["El Qué vs el Cómo", "Calidad vs Comportamiento"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 'En metodología Ágil, ¿quién suele priorizar el Backlog?',
 '["Representa al negocio", "Product..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Scrum Master"},{"id":"B", "texto":"Product Owner"},{"id":"C", "texto":"El Desarrollador"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 '¿Qué es un "Bug"?',
 '["Error", "Fallo en el software"]'::jsonb,
 '{"min_caracteres": 10, "max_caracteres": 150}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Para qué sirve una entrevista de levantamiento de información?',
 '["Técnica de educción", "Hablar con el cliente"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Para programar el código"},{"id":"B", "texto":"Para entender las necesidades del usuario"},{"id":"C", "texto":"Para vender el producto"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Menciona 3 técnicas para recopilar requisitos.',
 '["Entrevistas...", "Encuestas..."]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Escribe el formato estándar de una Historia de Usuario.',
 '["Como [rol]...", "Quiero [acción]..."]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 200}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué diagrama UML usarías para mostrar los estados por los que pasa una orden de compra?',
 '["Inicio, Pendiente, Aprobado, Fin", "Máquina de..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Diagrama de Clases"},{"id":"B", "texto":"Diagrama de Estados"},{"id":"C", "texto":"Diagrama de Despliegue"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 '¿Qué es el criterio de aceptación?',
 '["Condiciones para dar por terminada una tarea", "Definition of Done"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 'En BPMN, ¿qué representa un carril (Swimlane)?',
 '["Responsabilidad", "Actor o departamento"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Una decisión lógica"},{"id":"B", "texto":"Un actor o rol responsable de las tareas"},{"id":"C", "texto":"El flujo de datos"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Explica qué es la Trazabilidad de Requisitos.',
 '["Seguir la vida de un requisito", "Desde el origen hasta el código"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 '¿Cuál es la diferencia entre un prototipo de baja y alta fidelidad?',
 '["Papel vs Interactivo", "Detalle visual"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué es una prueba UAT?',
 '["User Acceptance Testing", "Prueba final"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Prueba Unitaria Automatizada"},{"id":"B", "texto":"Prueba de Aceptación de Usuario"},{"id":"C", "texto":"Prueba de Carga"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 'Si un requisito cambia a mitad del Desarrollo en un entorno Waterfall, ¿qué suele pasar?',
 '["Control de cambios", "Costoso"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Se adapta inmediatamente sin costo"},{"id":"B", "texto":"Requiere un proceso formal de control de cambios y suele ser costoso"},{"id":"C", "texto":"Se ignora el cambio"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Describe el concepto de "Happy Path".',
 '["Camino ideal", "Sin errores"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 300}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué herramienta usarías para gestionar un Backlog?',
 '["Jira es la más famosa", "Trello"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Photoshop"},{"id":"B", "texto":"Jira / Azure PROps"},{"id":"C", "texto":"Visual Studio Code"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 '¿Cómo manejas a un Stakeholder que insiste en un requisito técnicamente inviable?',
 '["Negociación", "Alternativas"]'::jsonb,
 '{"min_caracteres": 100, "max_caracteres": 1000}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Realiza un análisis de brechas (Gap Analysis) breve para la migración de un sistema legado a la nube.',
 '["Estado actual vs Estado futuro", "Identificar lo que falta"]'::jsonb,
 '{"min_caracteres": 100, "max_caracteres": 1500}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es la Deuda Técnica desde la perspectiva del Analista de Negocio?',
 '["Costo futuro", "Atajos tomados hoy"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Dinero que se debe al proveedor"},{"id":"B", "texto":"Costo implícito de retrabajo futuro por elegir una solución rápida hoy"},{"id":"C", "texto":"Falta de presupuesto"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Describe cómo priorizar requisitos usando la técnica MoSCoW.',
 '["Must, Should, Could, Won''t", "Esencial vs Deseable"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 600}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'En un proyecto crítico, ¿cómo mitigas el riesgo de "Scope Creep" (Alcance no controlado)?',
 '["Límites claros", "Proceso de cambios estricto"]'::jsonb,
 '{"min_caracteres": 80, "max_caracteres": 800}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 'Diferencia estratégica entre BPM (Business Process Management) y BPR (Business Process Reengineering).',
 '["Mejora continua vs Cambio radical", "Evolución vs Revolución"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"BPM es radical, BPR es incremental"},{"id":"B", "texto":"BPM es mejora continua, BPR es rediseño radical desde cero"},{"id":"C", "texto":"Son lo mismo"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 '¿Qué valor aporta un Diagrama de Secuencia en la fase de diseño técnico?',
 '["Interacción entre objetos", "Tiempo y mensajes"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Ante dos departamentos con requisitos contradictorios, ¿cuál es tu estrategia de resolución?',
 '["Facilitador", "Objetivos de negocio superiores"]'::jsonb,
 '{"min_caracteres": 80, "max_caracteres": 1000}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Explica el concepto de MVP (Producto Mínimo Viable) a un cliente que quiere "todo el sistema terminado ya".',
 '["Valor inmediato", "Aprendizaje validado"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 800}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 '¿Qué métrica utilizarías para evaluar la calidad de los requisitos definidos?',
 '["Tasa de defectos en requisitos", "Claridad y Completitud"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Líneas de código generadas"},{"id":"B", "texto":"Número de cambios solicitados post-aprobación (volatilidad)"},{"id":"C", "texto":"Horas de reunión"}], "respuesta_correcta":"B"}'::jsonb
);

-- 2. ADMINISTRADOR DE EMPRESA (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué significa las siglas FODA?',
 '["Análisis estratégico", "Fortalezas..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Finanzas, Organización, Dirección, Administración"},{"id":"B", "texto":"Fortalezas, Oportunidades, Debilidades, Amenazas"},{"id":"C", "texto":"Fondo de Ahorro"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Cuál es el objetivo principal de una empresa con fines de lucro?',
 '["Generar valor", "Rentabilidad"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Pagar impuestos"},{"id":"B", "texto":"Maximizar la riqueza de los accionistas/dueños"},{"id":"C", "texto":"Tener muchos empleados"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 'Define qué es un "Activo" en contabilidad.',
 '["Lo que tienes", "Recursos"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué documento muestra la estructura jerárquica de una empresa?',
 '["Mapa visual de cargos", "Árbol"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Balance General"},{"id":"B", "texto":"Organigrama"},{"id":"C", "texto":"Flujograma"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 '¿Qué es la Eficacia?',
 '["Lograr el objetivo", "Diferente a Eficiencia"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 '¿Cuál es la función principal del departamento de Recursos Humanos?',
 '["Gestión de talento", "Contratación"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 'En la mezcla de marketing (4P), ¿cuáles son las 4 P?',
 '["Producto...", "Precio..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Producto, Precio, Plaza, Promoción"},{"id":"B", "texto":"Personal, Proceso, Planta, Producción"},{"id":"C", "texto":"Planificación, Poder, Política, Prensa"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué significa B2B?',
 '["Tipo de comercio", "Business to..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Business to Business"},{"id":"B", "texto":"Business to Buyer"},{"id":"C", "texto":"Back to Basics"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 'Define "Costos Fijos".',
 '["No varían con la producción", "Alquiler, sueldos base"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Quién es la máxima autoridad formal en una Sociedad Anónima?',
 '["Representa a los accionistas", "Junta..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"El Gerente General"},{"id":"B", "texto":"La Junta de Accionistas"},{"id":"C", "texto":"El Contador"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 'Explica qué son los objetivos SMART.',
 '["Específicos, Medibles...", "Acrónimo en Inglés"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Cuál es la diferencia entre Liderazgo Transaccional y Transformacional?',
 '["Intercambio vs Inspiración", "Premios vs Visión"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué mide el KPI "Rotación de Personal"?',
 '["Entradas y salidas", "Retención"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"La velocidad de trabajo"},{"id":"B", "texto":"El porcentaje de empleados que abandonan la organización en un periodo"},{"id":"C", "texto":"El cambio de puestos internos"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 'Calcula el Punto de Equilibrio si: Costos Fijos = 1000, Precio = 50, Costo Variable = 30.',
 '["Fórmula: CF / (P - CV)", "Margen de contribución es 20"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"20 unidades"},{"id":"B", "texto":"50 unidades"},{"id":"C", "texto":"100 unidades"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Qué es un Diagrama de Gantt?',
 '["Gestión de proyectos", "Cronograma visual"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué estado financiero muestra la rentabilidad de la empresa en un periodo determinado?',
 '["Ingresos - Gastos", "Estado de Resultados"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Balance General"},{"id":"B", "texto":"Estado de Resultados (P&L)"},{"id":"C", "texto":"Flujo de Caja"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 'Define la técnica de feedback "Sandwich".',
 '["Positivo - Mejora - Positivo", "Suavizar la crítica"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Qué es el Clima Organizacional?',
 '["Percepción de los empleados", "Ambiente"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Cuál es la ventaja competitiva según Michael Porter?',
 '["Diferenciación o Costos", "Lo que te hace único"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Tener más dinero"},{"id":"B", "texto":"Una característica que permite superar a los rivales de manera sostenible"},{"id":"C", "texto":"Bajar los precios siempre"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 'En gestión de inventarios, ¿qué es el método FIFO?',
 '["Lo primero que entra...", "First In First Out"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Primero en Entrar, Primero en Salir"},{"id":"B", "texto":"Último en Entrar, Primero en Salir"},{"id":"C", "texto":"Promedio Ponderado"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Describe las 5 Fuerzas de Porter.',
 '["Proveedores, Clientes, Nuevos entrantes...", "Rivalidad"]'::jsonb,
 '{"min_caracteres": 100, "max_caracteres": 1000}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Cuál es la diferencia financiera entre CAPEX y OPEX?',
 '["Inversión vs Gasto operativo", "Largo plazo vs Día a día"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 600}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 'En una fusión de empresas (M&A), ¿cuál es el mayor riesgo cultural?',
 '["Choque de culturas", "Resistencia al cambio"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Cambio de logo"},{"id":"B", "texto":"Pérdida de talento clave por choque cultural"},{"id":"C", "texto":"Aumento de capital"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica el concepto de "Balanced Scorecard" (Cuadro de Mando Integral).',
 '["Kaplan y Norton", "4 perspectivas"]'::jsonb,
 '{"min_caracteres": 80, "max_caracteres": 800}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Cómo manejarías una reducción de personal del 20% para minimizar el impacto en la moral de los restantes?',
 '["Comunicación transparente", "Outplacement"]'::jsonb,
 '{"min_caracteres": 100, "max_caracteres": 1500}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el EBITDA y por qué es importante para valorar una empresa?',
 '["Earnings Before...", "Operatividad pura"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Muestra la utilidad neta final"},{"id":"B", "texto":"Muestra la capacidad de generar efectivo operativo puro, sin impuestos ni intereses"},{"id":"C", "texto":"Es el total de ventas"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Estrategia de Océano Azul: descríbela.',
 '["Crear nuevos mercados", "Hacer la competencia irrelevante"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 600}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 'En Responsabilidad Social Empresarial (RSE), ¿qué es el concepto de "Triple Bottom Line"?',
 '["Personas, Planeta, Beneficio", "3P"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Social, Ambiental, Económico"},{"id":"B", "texto":"Ventas, Costos, Utilidad"},{"id":"C", "texto":"Clientes, Proveedores, Estado"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Qué harías si tu principal proveedor sube los precios un 30% repentinamente?',
 '["Cadena de suministro", "Diversificación"]'::jsonb,
 '{"min_caracteres": 80, "max_caracteres": 1000}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica qué es el ROI y cómo se calcula.',
 '["Retorno de Inversión", "(Ganancia - Inversión) / Inversión"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
);

-- 3. INGENIERÍA INFORMÁTICA (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la unidad mínima de información en un computador?',
 '["0 o 1", "Bi..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Byte"},{"id":"B", "texto":"Bit"},{"id":"C", "texto":"Hertz"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué sistema numérico utilizan internamente los computadores?',
 '["Base 2", "Ceros y unos"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Decimal"},{"id":"B", "texto":"Hexadecimal"},{"id":"C", "texto":"Binario"}], "respuesta_correcta":"C"}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 'Diferencia básica entre RAM y ROM.',
 '["Volátil vs No volátil", "Lectura/Escritura vs Solo lectura"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la función principal de un Sistema Operativo?',
 '["Intermediario", "Gestión de recursos"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Editar textos"},{"id":"B", "texto":"Gestionar el hardware y proveer servicios a los programas"},{"id":"C", "texto":"Navegar por internet"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Qué es una dirección IP?',
 '["Identificador de red", "Como un número de teléfono"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué significan las siglas CPU?',
 '["Cerebro del PC", "Central..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Central Processing Unit"},{"id":"B", "texto":"Computer Personal Unit"},{"id":"C", "texto":"Central Power Unit"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 'En lógica booleana, ¿cuál es el resultado de 1 AND 0?',
 '["Ambos deben ser 1", "Multiplicación lógica"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"1"},{"id":"B", "texto":"0"},{"id":"C", "texto":"Null"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Qué es el Hardware?',
 '["Parte física", "Lo que puedes tocar"]'::jsonb,
 '{"min_caracteres": 10, "max_caracteres": 150}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Para qué sirve un algoritmo?',
 '["Secuencia de pasos", "Resolver problemas"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es el componente encargado de los gráficos en un PC?',
 '["GPU", "Tarjeta..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"CPU"},{"id":"B", "texto":"GPU"},{"id":"C", "texto":"SSD"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 'Explica qué es la virtualización.',
 '["Máquinas virtuales", "Abstraer hardware"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿En qué capa del modelo OSI funciona el protocolo IP?',
 '["Red", "Capa 3"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Capa 2 (Enlace)"},{"id":"B", "texto":"Capa 3 (Red)"},{"id":"C", "texto":"Capa 4 (Transporte)"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es RAID 1 y para qué sirve?',
 '["Espejo", "Redundancia"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 'Diferencia entre TCP y UDP.',
 '["Fiabilidad vs Velocidad", "Conexión vs Sin conexión"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"TCP es más rápido, UDP es seguro"},{"id":"B", "texto":"TCP garantiza entrega (orientado a conexión), UDP no (streaming)"},{"id":"C", "texto":"Son iguales"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es la Normalización en Bases de Datos?',
 '["Evitar redundancia", "Formas normales"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿Qué función cumple un servidor DNS?',
 '["Traduce nombres a IP", "Directorio telefónico de internet"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Asigna IPs dinámicas"},{"id":"B", "texto":"Traduce nombres de dominio a direcciones IP"},{"id":"C", "texto":"Encripta la conexión"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 'Describe el concepto de "Cloud Computing".',
 '["Servicios a través de internet", "Bajo demanda"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿Qué es un Firewall?',
 '["Cortafuegos", "Seguridad de red"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Un antivirus"},{"id":"B", "texto":"Sistema que controla el tráfico de red entrante y saliente"},{"id":"C", "texto":"Un cable de red blindado"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es el Kernel de un Sistema Operativo?',
 '["Núcleo", "Control directo del hardware"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 'En criptografía asimétrica, ¿qué clave se comparte públicamente?',
 '["Pública vs Privada", "Para encriptar o verificar"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Clave Privada"},{"id":"B", "texto":"Clave Pública"},{"id":"C", "texto":"Ninguna"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'Diseña una arquitectura de Alta Disponibilidad (HA) básica para una web crítica.',
 '["Balanceadores", "Redundancia", "Multi-AZ"]'::jsonb,
 '{"min_caracteres": 80, "max_caracteres": 1000}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'Explica el funcionamiento de un ataque DDoS y cómo mitigarlo.',
 '["Denegación distribuida", "CDN, WAF"]'::jsonb,
 '{"min_caracteres": 60, "max_caracteres": 800}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué es un Container Orchestrator (ej: Kubernetes) y por qué es necesario en grandes sistemas?',
 '["Gestión de ciclo de vida", "Escalado automático"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Es un antivirus para contenedores"},{"id":"B", "texto":"Automatiza el despliegue, escalado y gestión de aplicaciones en contenedores"},{"id":"C", "texto":"Es un lenguaje de programación"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 'Diferencia entre Escalado Vertical y Horizontal.',
 '["Más potencia vs Más máquinas", "CPU vs Nodos"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Vertical es agregar más máquinas, Horizontal es mejorar la máquina"},{"id":"B", "texto":"Vertical es mejorar la máquina (más RAM/CPU), Horizontal es agregar más máquinas"},{"id":"C", "texto":"Son lo mismo"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es "Infrastructure as Code" (IaC)?',
 '["Terraform, Ansible", "Infraestructura programable"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'En el contexto de Big Data, explica las 3 V.',
 '["Volumen, Velocidad, Variedad", "Datos masivos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es un plan de DRP (Disaster Recovery Plan)?',
 '["Recuperación ante desastres", "Continuidad de negocio"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 600}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 'Explica el concepto de "Zero Trust Security".',
 '["No confiar en nadie", "Verificar siempre"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Confiar solo en la red interna"},{"id":"B", "texto":"Modelo donde no se confía en ningún usuario o dispositivo, dentro o fuera del perímetro"},{"id":"C", "texto":"No usar contraseñas"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es Latencia y cómo afecta a los sistemas distribuidos?',
 '["Retardo", "Tiempo de viaje del paquete"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la principal ventaja de usar una arquitectura "Serverless"?',
 '["No gestionas servidores", "Pago por uso"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Mayor control del hardware"},{"id":"B", "texto":"Abstracción total del servidor y modelo de costos por ejecución"},{"id":"C", "texto":"Es gratis"}], "respuesta_correcta":"B"}'::jsonb
);

-- 4. DESARROLLADOR (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué imprime "console.log(typeof [])" en JavaScript?',
 '["Arrays son objetos", "Curiosidad de JS"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"array"},{"id":"B", "texto":"object"},{"id":"C", "texto":"list"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Para qué sirve el operador "++" en muchos lenguajes?',
 '["Incremento", "Sumar uno"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Suma dos variables"},{"id":"B", "texto":"Incrementa el valor de la variable en 1"},{"id":"C", "texto":"Concatena strings"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un bucle "infinito"?',
 '["Nunca termina", "Condición siempre true"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 'En Git, ¿qué comando descarga los cambios del remoto al local?',
 '["Traer cambios", "Pull..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"git push"},{"id":"B", "texto":"git pull"},{"id":"C", "texto":"git commit"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es una variable?',
 '["Espacio de memoria", "Contenedor"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 'En CSS, ¿qué propiedad cambia el color de fondo?',
 '["Background...", "Color es para texto"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"color"},{"id":"B", "texto":"background-color"},{"id":"C", "texto":"border"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es el DOM en Desarrollor web?',
 '["Document Object Model", "Árbol de elementos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Cuál es el índice del primer elemento en un array (en la mayoría de lenguajes)?',
 '["Empieza en...", "Cero"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"0"},{"id":"B", "texto":"1"},{"id":"C", "texto":"-1"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué significa IDE?',
 '["Entorno de Desarrollor", "Integrated..."]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Integrated PRelopment Environment"},{"id":"B", "texto":"Internet PRelopment Explorer"},{"id":"C", "texto":"Internal Data Exchange"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 'Escribe una función simple que sume dos números (pseudocódigo).',
 '["function suma(a,b)...", "return..."]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la Inyección de Dependencias?',
 '["Patrón de diseño", "Inversión de control"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'En una API REST, ¿qué verbo HTTP se usa para actualizar parcialmente un recurso?',
 '["No es PUT", "Parcial"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"PUT"},{"id":"B", "texto":"PATCH"},{"id":"C", "texto":"POST"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 'Explica el concepto de "Callback" en programación asíncrona.',
 '["Función pasada como argumento", "Se ejecuta después"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué diferencia hay entre "git merge" y "git rebase"?',
 '["Historial lineal vs Historial ramificado", "Reescritura"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Merge reescribe la historia, Rebase crea un commit de unión"},{"id":"B", "texto":"Rebase reescribe la historia linealmente, Merge crea un commit de unión"},{"id":"C", "texto":"Son idénticos"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un ORM?',
 '["Object Relational Mapping", "Base de datos como objetos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'En POO, ¿qué es el Polimorfismo?',
 '["Muchas formas", "Mismo método, diferente comportamiento"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"La capacidad de heredar atributos"},{"id":"B", "texto":"Capacidad de objetos de diferentes clases de responder al mismo mensaje de distinta manera"},{"id":"C", "texto":"Ocultar datos privados"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es el "Scope" (alcance) de una variable?',
 '["Dónde vive la variable", "Global vs Local"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Por qué usarías Docker en Desarrollor?',
 '["Entornos consistentes", "Funciona en mi máquina"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Para hacer el código más rápido"},{"id":"B", "texto":"Para garantizar paridad entre entornos de Desarrollor y producción"},{"id":"C", "texto":"Para diseñar interfaces"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es MVC?',
 '["Modelo Vista Controlador", "Patrón de arquitectura"]'::jsonb,
 '{"min_caracteres": 20, "max_caracteres": 200}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'Identifica el error: "SELECT * FROM users WHERE name = ''Pepe"',
 '["Faltan comillas", "Sintaxis SQL"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Falta cerrar la comilla simple"},{"id":"B", "texto":"Falta el punto y coma"},{"id":"C", "texto":"Users va con mayúscula"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'Explica qué es una "Race Condition" (Condición de Carrera).',
 '["Concurrencia", "Resultados impredecibles"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'En Arquitectura de Software, ¿qué es el patrón Singleton y cuándo es peligroso?',
 '["Instancia única", "Estado global mutable"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 500}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué principio SOLID se viola si una clase tiene demasiadas responsabilidades?',
 '["Single Responsibility", "La S de SOLID"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"SRP (Single Responsibility Principle)"},{"id":"B", "texto":"OCP (Open/Closed Principle)"},{"id":"C", "texto":"LSP (Liskov Substitution Principle)"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un "Memory Leak" y cómo lo detectas?',
 '["Fuga de memoria", "El consumo de RAM crece sin parar"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 600}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'Comparación: Monolito vs Microservicios. ¿Cuándo NO usarías microservicios?',
 '["Complejidad", "Equipos pequeños"]'::jsonb,
 '{"min_caracteres": 60, "max_caracteres": 800}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 'En bases de datos, ¿qué es una transacción ACID?',
 '["Atomicidad, Consistencia...", "Todo o nada"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Un tipo de base de datos NoSQL"},{"id":"B", "texto":"Un conjunto de propiedades que garantizan la validez de las transacciones"},{"id":"C", "texto":"Un virus informático"}], "respuesta_correcta":"B"}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la complejidad ciclomática?',
 '["Métrica de código", "Caminos independientes"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 'Estrategias de Caché: Diferencia entre Cache-Aside y Write-Through.',
 '["Lectura vs Escritura", "Quién carga los datos"]'::jsonb,
 '{"opciones": [{"id":"A", "texto":"Cache-Aside la app carga los datos si no están; Write-Through escribe en caché y DB a la vez"},{"id":"B", "texto":"Son lo mismo"},{"id":"C", "texto":"Write-Through es solo para lectura"}], "respuesta_correcta":"A"}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la Idempotencia en una API REST?',
 '["Repetir la llamada", "Mismo resultado"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué es el teorema CAP?',
 '["Distribuido", "Escoge 2 de 3"]'::jsonb,
 '{"opciones": [
    {"id":"A", "texto":"Consistency, Availability, Partition Tolerance"},
    {"id":"B", "texto":"Capacity, Availability, Performance"},
    {"id":"C", "texto":"Code, App, Program"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb
);

-- ====================================================================================
-- SOPORTE TI (5 preguntas - nivel básico) -- NV
-- ====================================================================================
-- ====================================================================================
-- SOPORTE TI (5 preguntas - nivel básico) -- NV
-- ====================================================================================
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un sistema operativo?',
 '["Windows, Linux, macOS", "Software base"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un programa que gestiona el hardware y software del computador"},
   {"id":"B", "texto":"Un antivirus"},
   {"id":"C", "texto":"Una aplicación de office"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué significa IP en redes?',
 '["Dirección de red", "Internet Protocol"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Internet Provider"},
   {"id":"B", "texto":"Internet Protocol"},
   {"id":"C", "texto":"Internal Program"}
 ], "respuesta_correcta":"B"}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Cuál es la función del protocolo DHCP?',
 '["Asigna direcciones", "Automático"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Asignar direcciones IP automáticamente"},
   {"id":"B", "texto":"Proteger contra virus"},
   {"id":"C", "texto":"Comprimir archivos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué comando usarías para verificar la conectividad de red en Windows?',
 '["Verificar conexión", "Ping..."]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"ipconfig"},
   {"id":"B", "texto":"ping"},
   {"id":"C", "texto":"netstat"}
 ], "respuesta_correcta":"B"}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un firewall?',
 '["Protección de red", "Bloquea tráfico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un sistema que controla el tráfico de red entrante y saliente"},
   {"id":"B", "texto":"Un tipo de cable de red"},
   {"id":"C", "texto":"Un servidor web"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- DEVOPS ENGINEER (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'DevOps Engineer', 'opcion_multiple',
 '¿Qué es Docker?',
 '["Contenedores", "Portable"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Una plataforma de contenedores"},
   {"id":"B", "texto":"Un lenguaje de programación"},
   {"id":"C", "texto":"Una base de datos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Qué es CI/CD?',
 '["Integración continua", "Despliegue continuo"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Continuous Integration/Continuous Deployment"},
   {"id":"B", "texto":"Central Information Control Data"},
   {"id":"C", "texto":"Computer Integration Code Development"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Qué es Kubernetes?',
 '["Orquestación", "K8s"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un sistema de orquestación de contenedores"},
   {"id":"B", "texto":"Un editor de código"},
   {"id":"C", "texto":"Un framework de testing"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Para qué sirve Terraform?',
 '["Infrastructure as Code", "IaC"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para definir infraestructura como código"},
   {"id":"B", "texto":"Para compilar código"},
   {"id":"C", "texto":"Para hacer testing"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'DevOps Engineer', 'abierta',
 '¿Qué es una pipeline de CI/CD?',
 '["Automatización", "Build, test, deploy"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb
),

-- ====================================================================================
-- SYSADMIN (5 preguntas - nivel básico/intermedio)
-- ====================================================================================
('NV', 'TI', 'jr', 'SysAdmin', 'opcion_multiple',
 '¿Qué es un servidor?',
 '["Computador que provee servicios", "Siempre encendido"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un computador que provee servicios a otros equipos"},
   {"id":"B", "texto":"Un tipo de cable"},
   {"id":"C", "texto":"Una aplicación móvil"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'TI', 'jr', 'SysAdmin', 'opcion_multiple',
 '¿Qué comando en Linux muestra los procesos en ejecución?',
 '["Ver procesos", "ps, top"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"ls"},
   {"id":"B", "texto":"ps"},
   {"id":"C", "texto":"cd"}
 ], "respuesta_correcta":"B"}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
 '¿Qué es un backup incremental?',
 '["Solo cambios", "Vs completo"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Copia solo los cambios desde el último backup"},
   {"id":"B", "texto":"Copia todos los archivos siempre"},
   {"id":"C", "texto":"Elimina archivos antiguos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
 '¿Qué puerto usa SSH por defecto?',
 '["Secure Shell", "22"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"80"},
   {"id":"B", "texto":"22"},
   {"id":"C", "texto":"443"}
 ], "respuesta_correcta":"B"}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'abierta',
 'Explica qué es un RAID y para qué sirve',
 '["Redundancia", "Varios discos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),

-- ====================================================================================
-- DESARROLLADOR BACKEND (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'Desarrollador Backend', 'opcion_multiple',
 '¿Qué es una API?',
 '["Application Programming Interface", "Comunicación entre apps"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Application Programming Interface"},
   {"id":"B", "texto":"Advanced Program Information"},
   {"id":"C", "texto":"Automatic Process Integration"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Backend', 'opcion_multiple',
 '¿Qué es REST?',
 '["Arquitectura de APIs", "HTTP"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un estilo arquitectónico para APIs web"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un lenguaje de programación"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Backend', 'abierta',
 '¿Qué diferencia hay entre SQL y NoSQL?',
 '["Estructurado vs No estructurado", "Relacional vs Documental"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Backend', 'opcion_multiple',
 '¿Qué es un middleware?',
 '["Intermediario", "Entre request y response"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Software que procesa peticiones entre cliente y servidor"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un framework frontend"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Backend', 'abierta',
 'Explica el patrón Repository en arquitectura de software',
 '["Separación de concerns", "Acceso a datos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),

-- ====================================================================================
-- DESARROLLADOR FRONTEND (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Qué es HTML?',
 '["Lenguaje de marcado", "Estructura web"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"HyperText Markup Language"},
   {"id":"B", "texto":"High Tech Modern Language"},
   {"id":"C", "texto":"Home Tool Making Language"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Para qué sirve CSS?',
 '["Estilos", "Diseño visual"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para dar estilos y diseño a páginas web"},
   {"id":"B", "texto":"Para programar la lógica"},
   {"id":"C", "texto":"Para bases de datos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Qué es el DOM?',
 '["Document Object Model", "Árbol de elementos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Document Object Model - representación de la página"},
   {"id":"B", "texto":"Data Operation Method"},
   {"id":"C", "texto":"Digital Online Manager"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Qué es React?',
 '["Librería JS", "Componentes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Una librería de JavaScript para construir interfaces"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un servidor web"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Frontend', 'abierta',
 'Explica qué es el Virtual DOM y por qué React lo usa',
 '["Rendimiento", "Comparación"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),

-- ====================================================================================
-- DESARROLLADOR FULLSTACK (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué significa Full Stack?',
 '["Frontend + Backend", "Completo"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Desarrollador que trabaja tanto en frontend como backend"},
   {"id":"B", "texto":"Desarrollador solo de bases de datos"},
   {"id":"C", "texto":"Desarrollador solo de diseño"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es Node.js?',
 '["JavaScript en servidor", "Runtime"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un entorno de ejecución de JavaScript en el servidor"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un framework de CSS"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es una SPA (Single Page Application)?',
 '["Una sola página", "Carga dinámica"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Aplicación que carga una sola página y actualiza contenido dinámicamente"},
   {"id":"B", "texto":"Aplicación con muchas páginas"},
   {"id":"C", "texto":"Aplicación móvil"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es CORS?',
 '["Cross-Origin", "Seguridad"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Cross-Origin Resource Sharing - mecanismo de seguridad"},
   {"id":"B", "texto":"Central Online Resource System"},
   {"id":"C", "texto":"Computer Operating Resource Server"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Fullstack', 'abierta',
 'Explica la diferencia entre autenticación y autorización',
 '["Quién eres vs Qué puedes hacer", "Login vs Permisos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb
),

-- ====================================================================================
-- DESARROLLADOR ANDROID (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué lenguaje es nativo para Android?',
 '["Kotlin, Java", "Android"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Kotlin y Java"},
   {"id":"B", "texto":"Python"},
   {"id":"C", "texto":"Ruby"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es una Activity en Android?',
 '["Pantalla", "Componente UI"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Una pantalla/interfaz de usuario"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un servicio en background"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es un Intent en Android?',
 '["Mensajería", "Comunicación entre componentes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un mensaje para comunicar componentes"},
   {"id":"B", "texto":"Una variable"},
   {"id":"C", "texto":"Un tipo de error"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es el AndroidManifest.xml?',
 '["Configuración de app", "Permisos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Archivo de configuración de la aplicación"},
   {"id":"B", "texto":"Código fuente principal"},
   {"id":"C", "texto":"Base de datos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Android', 'abierta',
 'Explica el ciclo de vida de una Activity',
 '["onCreate, onStart, onResume...", "Estados"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 400}'::jsonb
),

-- ====================================================================================
-- QA AUTOMATION (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Desarrollo', 'jr', 'QA Automation', 'opcion_multiple',
 '¿Qué es el testing automatizado?',
 '["Scripts de prueba", "Automático"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Pruebas ejecutadas por scripts sin intervención manual"},
   {"id":"B", "texto":"Pruebas manuales"},
   {"id":"C", "texto":"Diseño de interfaces"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'QA Automation', 'opcion_multiple',
 '¿Qué es un test case?',
 '["Caso de prueba", "Escenario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un escenario de prueba con pasos y resultado esperado"},
   {"id":"B", "texto":"Un error en el código"},
   {"id":"C", "texto":"Una función del programa"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'QA Automation', 'opcion_multiple',
 '¿Qué es Selenium?',
 '["Automatización web", "Testing"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Herramienta para automatizar pruebas de aplicaciones web"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un lenguaje de programación"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'QA Automation', 'abierta',
 'Diferencia entre testing unitario e integración',
 '["Función vs Múltiples componentes", "Aislado vs Conjunto"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'QA Automation', 'abierta',
 '¿Qué es el patrón Page Object Model (POM)?',
 '["Patrón de diseño", "Mantenibilidad"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),

-- ====================================================================================
-- ANALISTA DE DATOS (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Analisis TI', 'jr', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es SQL?',
 '["Lenguaje de consultas", "Bases de datos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Structured Query Language - para consultar bases de datos"},
   {"id":"B", "texto":"Simple Question Language"},
   {"id":"C", "texto":"System Quality Level"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es un dashboard?',
 '["Tablero de visualización", "Gráficos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Panel visual que muestra métricas e indicadores clave"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un tipo de gráfico"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es ETL?',
 '["Extract, Transform, Load", "Proceso de datos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Extract, Transform, Load - proceso de integración de datos"},
   {"id":"B", "texto":"Error Testing Language"},
   {"id":"C", "texto":"External Tool Library"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Datos', 'abierta',
 'Explica qué es la normalización de datos',
 '["Estructurar datos", "Eliminar redundancia"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es un Data Warehouse?',
 '["Almacén de datos", "Histórico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema centralizado para almacenar y analizar grandes volúmenes de datos"},
   {"id":"B", "texto":"Una hoja de cálculo"},
   {"id":"C", "texto":"Un tipo de gráfico"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- ANALISTA DE NEGOCIOS (5 preguntas - niveles variados)
-- ====================================================================================
('NV', 'Analisis TI', 'jr', 'Analista de Negocios', 'opcion_multiple',
 '¿Qué es un requerimiento funcional?',
 '["Qué debe hacer el sistema", "Funcionalidades"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Descripción de una funcionalidad que el sistema debe tener"},
   {"id":"B", "texto":"Hardware necesario"},
   {"id":"C", "texto":"Costo del proyecto"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista de Negocios', 'opcion_multiple',
 '¿Qué es un stakeholder?',
 '["Interesado", "Afectado por el proyecto"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Persona u organización con interés en el proyecto"},
   {"id":"B", "texto":"Un tipo de software"},
   {"id":"C", "texto":"Una metodología"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Negocios', 'opcion_multiple',
 '¿Qué es un caso de uso?',
 '["Interacción usuario-sistema", "Escenario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Descripción de cómo un usuario interactúa con el sistema"},
   {"id":"B", "texto":"Un error en el software"},
   {"id":"C", "texto":"Una prueba técnica"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Negocios', 'abierta',
 'Diferencia entre requerimiento funcional y no funcional',
 '["Qué hace vs Cómo lo hace", "Funcionalidad vs Calidad"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista de Negocios', 'abierta',
 '¿Qué es el análisis de brecha (gap analysis)?',
 '["Estado actual vs deseado", "Diferencia"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb
),

-- ====================================================================================
-- ANALISTA QA (5 preguntas - nivel básico/intermedio)
-- ====================================================================================
('NV', 'Analisis TI', 'jr', 'Analista QA', 'opcion_multiple',
 '¿Qué significa QA?',
 '["Quality Assurance", "Calidad"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Quality Assurance - Aseguramiento de Calidad"},
   {"id":"B", "texto":"Quick Access"},
   {"id":"C", "texto":"Question Answer"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista QA', 'opcion_multiple',
 '¿Qué es un bug?',
 '["Error en software", "Defecto"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Error o defecto en el software"},
   {"id":"B", "texto":"Una funcionalidad nueva"},
   {"id":"C", "texto":"Un tipo de virus"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista QA', 'opcion_multiple',
 '¿Qué es el testing de regresión?',
 '["Verificar que nada se rompió", "Después de cambios"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Pruebas para verificar que cambios no afectaron funcionalidad existente"},
   {"id":"B", "texto":"Pruebas solo de nuevas funciones"},
   {"id":"C", "texto":"Pruebas de rendimiento"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista QA', 'abierta',
 'Explica la diferencia entre verificación y validación',
 '["¿Lo hicimos bien? vs ¿Hicimos lo correcto?", "Proceso vs Producto"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista QA', 'opcion_multiple',
 '¿Qué es un plan de pruebas?',
 '["Documento", "Estrategia de testing"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que define estrategia, alcance y recursos de testing"},
   {"id":"B", "texto":"Lista de bugs"},
   {"id":"C", "texto":"Manual de usuario"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- ANALISTA FUNCIONAL (5 preguntas - nivel intermedio)
-- ====================================================================================
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 '¿Cuál es el rol principal de un Analista Funcional?',
 '["Puente negocio-TI", "Requerimientos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Traducir necesidades de negocio a requerimientos técnicos"},
   {"id":"B", "texto":"Programar aplicaciones"},
   {"id":"C", "texto":"Gestionar servidores"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 '¿Qué es un diagrama de flujo?',
 '["Representación visual de proceso", "Pasos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Representación gráfica de un proceso o algoritmo"},
   {"id":"B", "texto":"Una tabla de datos"},
   {"id":"C", "texto":"Un reporte"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 '¿Qué es la especificación funcional?',
 '["Documento detallado", "Cómo debe funcionar"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que describe en detalle cómo debe funcionar el sistema"},
   {"id":"B", "texto":"Manual de usuario"},
   {"id":"C", "texto":"Código fuente"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'abierta',
 'Explica qué es el modelado de procesos de negocio',
 '["BPM", "Representar flujos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista Funcional', 'abierta',
 '¿Qué técnicas usarías para elicitar requerimientos?',
 '["Entrevistas, talleres, observación", "Múltiples técnicas"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),

-- ====================================================================================
-- ASISTENTE ADMINISTRATIVO (5 preguntas - nivel básico)
-- ====================================================================================
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es Microsoft Excel?',
 '["Hoja de cálculo", "Tablas y fórmulas"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Programa de hojas de cálculo"},
   {"id":"B", "texto":"Editor de imágenes"},
   {"id":"C", "texto":"Base de datos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Para qué sirve una agenda digital?',
 '["Organizar tareas", "Calendario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para organizar eventos, reuniones y tareas"},
   {"id":"B", "texto":"Para editar videos"},
   {"id":"C", "texto":"Para programar"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es un correo corporativo?',
 '["Email profesional", "Dominio de empresa"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Cuenta de email profesional con dominio de la empresa"},
   {"id":"B", "texto":"Correo personal"},
   {"id":"C", "texto":"Red social"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es un acta de reunión?',
 '["Documento de registro", "Minuta"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que registra lo tratado en una reunión"},
   {"id":"B", "texto":"Invitación a reunión"},
   {"id":"C", "texto":"Lista de asistentes"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es la gestión documental?',
 '["Organización de archivos", "Sistema"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema para organizar, almacenar y recuperar documentos"},
   {"id":"B", "texto":"Edición de textos"},
   {"id":"C", "texto":"Impresión de documentos"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- ANALISTA CONTABLE (5 preguntas - nivel básico/intermedio)
-- ====================================================================================
('NV', 'Administracion', 'jr', 'Analista Contable', 'opcion_multiple',
 '¿Qué es un balance general?',
 '["Estado financiero", "Activos, pasivos, patrimonio"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Estado financiero que muestra activos, pasivos y patrimonio"},
   {"id":"B", "texto":"Lista de empleados"},
   {"id":"C", "texto":"Presupuesto mensual"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'jr', 'Analista Contable', 'abierta',
 '¿Qué significa débito y crédito en contabilidad?',
 '["Partida doble", "Cargo y abono"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué es la conciliación bancaria?',
 '["Comparar registros", "Libro vs Banco"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Proceso de comparar registros contables con extractos bancarios"},
   {"id":"B", "texto":"Transferencia bancaria"},
   {"id":"C", "texto":"Solicitud de préstamo"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué son las cuentas por pagar?',
 '["Obligaciones", "Deudas"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Deudas u obligaciones que la empresa debe pagar"},
   {"id":"B", "texto":"Dinero que nos deben"},
   {"id":"C", "texto":"Ingresos futuros"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué es la depreciación?',
 '["Pérdida de valor", "Desgaste"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Pérdida de valor de un activo con el tiempo"},
   {"id":"B", "texto":"Aumento de precio"},
   {"id":"C", "texto":"Tipo de impuesto"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- ENCARGADO DE ADMINISTRACIÓN (5 preguntas - nivel intermedio)
-- ====================================================================================
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es la gestión de recursos humanos?',
 '["Administrar personal", "Reclutamiento, capacitación"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Proceso de administrar el personal de la organización"},
   {"id":"B", "texto":"Compra de equipos"},
   {"id":"C", "texto":"Gestión financiera"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es un presupuesto?',
 '["Plan financiero", "Ingresos y gastos proyectados"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Plan que estima ingresos y gastos futuros"},
   {"id":"B", "texto":"Informe de ventas"},
   {"id":"C", "texto":"Lista de productos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'abierta',
 'Explica qué es un indicador de gestión (KPI)',
 '["Key Performance Indicator", "Medir desempeño"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es la cadena de suministro?',
 '["Supply Chain", "Proveedores a clientes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Red de proveedores, fabricantes y distribuidores"},
   {"id":"B", "texto":"Lista de empleados"},
   {"id":"C", "texto":"Catálogo de productos"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es el control interno?',
 '["Procesos de control", "Prevenir fraudes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema de políticas y procedimientos para proteger activos"},
   {"id":"B", "texto":"Auditoría externa"},
   {"id":"C", "texto":"Seguridad física"}
 ], "respuesta_correcta":"A"}'::jsonb
),

-- ====================================================================================
-- JEFE DE ADMINISTRACIÓN (5 preguntas - nivel intermedio/avanzado)
-- ====================================================================================
('NV', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es la planeación estratégica?',
 '["Objetivos a largo plazo", "Estrategia organizacional"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Proceso de definir objetivos y estrategias a largo plazo"},
   {"id":"B", "texto":"Plan de ventas mensual"},
   {"id":"C", "texto":"Lista de tareas diarias"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el análisis FODA?',
 '["Fortalezas, Oportunidades, Debilidades, Amenazas", "Diagnóstico estratégico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Herramienta para analizar fortalezas, oportunidades, debilidades y amenazas"},
   {"id":"B", "texto":"Tipo de presupuesto"},
   {"id":"C", "texto":"Sistema contable"}
 ], "respuesta_correcta":"A"}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica qué es el balanced scorecard (cuadro de mando integral)',
 '["Perspectivas múltiples", "Indicadores estratégicos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Qué es la gestión del cambio organizacional?',
 '["Change management", "Transición"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el ROI (Return on Investment)?',
 '["Retorno de inversión", "Rentabilidad"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Métrica que mide la rentabilidad de una inversión"},
   {"id":"B", "texto":"Tipo de impuesto"},
   {"id":"C", "texto":"Estado financiero"}
 ], "respuesta_correcta":"A"}'::jsonb
);

-- =============================================================================
-- INSERT PREGUNTAS HABILIDADES BLANDAS TI (4 preguntas - nivel básico)
-- =============================================================================
-- SOFT SKILLS - Soporte TI
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta) VALUES
('BL', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 'Un usuario muy molesto te llama porque “el computador no prende” justo antes de una reunión importante. ¿Cuál es la mejor forma de manejar la situación?',
 '["Empatía primero", "Haz preguntas claras sobre lo que ve en pantalla"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Decirle que no puedes ayudar porque tienes muchos tickets"},
    {"id":"B","texto":"Pedirle que lea el manual y volver a llamar si no resulta"},
    {"id":"C","texto":"Escuchar la situación, reconocer la urgencia y guiarlo paso a paso con preguntas simples"},
    {"id":"D","texto":"Derivarlo de inmediato a otra persona sin recopilar información"}
  ], "respuesta_correcta":"C"}'::jsonb
),

('BL', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Cuenta una ocasión en la que ayudaste a un usuario no técnico a resolver un problema con su equipo. ¿Qué hiciste y qué resultado tuviste?',
 '["Piensa en alguien real", "Describe qué hiciste tú y cómo terminó la situación"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 'Tienes un incidente que afecta a toda una gerencia y varios tickets menores (por ejemplo, cambio de contraseña). ¿Cómo deberías priorizar?',
 '["Impacto en el negocio", "Comunica tiempos a los demás usuarios"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Atender todos en orden de llegada para ser justo"},
    {"id":"B","texto":"Atender primero los más rápidos para bajar la cola"},
    {"id":"C","texto":"Priorizar el incidente crítico, informar a los demás usuarios sobre la demora y actualizar el estado de sus tickets"},
    {"id":"D","texto":"Cerrar los tickets menores sin avisar para concentrarte en el incidente crítico"}
  ], "respuesta_correcta":"C"}'::jsonb
),

('BL', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Describe una situación en la que lideraste la resolución de un problema crítico que afectaba la continuidad de las operaciones. ¿Cómo coordinaste al equipo y qué aprendieron?',
 '["Piensa en un incidente crítico", "Cuenta qué hizo el equipo y qué hiciste tú"]'::jsonb,
 '{"min_caracteres":120,"max_caracteres":1000,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - DevOps Engineer
('BL', 'TI', 'jr', 'DevOps Engineer', 'opcion_multiple',
 'Estás automatizando un proceso sencillo y tu script rompe el pipeline de CI. ¿Qué deberías hacer?',
 '["Piensa en responsabilidad", "Aprendizaje del error"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Borrar el script y hacer como si nada hubiera pasado"},
    {"id":"B","texto":"Culpar a la herramienta de CI por ser poco estable"},
    {"id":"C","texto":"Comunicar el problema, revertir el cambio, analizar la causa y proponer una corrección"},
    {"id":"D","texto":"Esperar a que alguien más lo arregle"}
  ], "respuesta_correcta":"C"}'::jsonb
),

('BL', 'TI', 'jr', 'DevOps Engineer', 'abierta',
 'Cuenta una experiencia en la que automatizaste una tarea manual (aunque fuera pequeña). ¿Qué problema resolviste y qué impacto tuvo en el equipo?',
 '["Piensa en algo real", "Explica qué cambió después de automatizar"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'TI', 'mid', 'DevOps Engineer', 'opcion_multiple',
 'El equipo de desarrollo quiere hacer un cambio urgente en producción sin usar el pipeline de CI/CD porque “no hay tiempo”. ¿Cuál es la mejor respuesta?',
 '["Riesgo vs velocidad", "Negocia sin ceder la calidad"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Aceptar y hacer el cambio manual sin registrar nada"},
    {"id":"B","texto":"Negarte sin explicar los motivos"},
    {"id":"C","texto":"Explicar los riesgos, buscar una alternativa rápida dentro del pipeline y dejar registro de la decisión tomada"},
    {"id":"D","texto":"Decir que lo hagan ellos y no involucrarte"}
  ], "respuesta_correcta":"C"}'::jsonb
),

('BL', 'TI', 'sr', 'DevOps Engineer', 'abierta',
 'Describe una situación en la que lideraste una mejora en la plataforma (por ejemplo, monitoreo, alertas o infraestructura como código) que redujo incidentes o tareas manuales. ¿Qué hiciste y qué resultados obtuviste?',
 '["Piensa en una mejora real", "Cuenta antes y después del cambio"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - SysAdmin
('BL', 'TI', 'jr', 'SysAdmin', 'opcion_multiple',
 'Un usuario interno reporta que “el sistema anda lento”, pero no entrega detalles. ¿Cómo deberías responder?',
 '["Haz preguntas concretas", "Mantén buena actitud con el cliente interno"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Decirle que seguramente es su computador y cerrar el ticket"},
    {"id":"B","texto":"Pedirle con calma más detalles (qué sistema, desde cuándo, qué ve en pantalla) y registrar la información en el ticket"},
    {"id":"C","texto":"Pedirle que mande un correo a otro equipo"},
    {"id":"D","texto":"Ignorar el ticket hasta que se vuelva crítico"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'jr', 'SysAdmin', 'abierta',
 'Cuenta una ocasión en la que registraste y seguiste un incidente hasta su cierre. ¿Cómo te aseguraste de dejar buena documentación para el equipo?',
 '["Piensa en un incidente real", "Menciona registro, seguimiento y cierre"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
 'Se genera una mesa de incidentes por caída de un servicio crítico. ¿Cuál es tu mejor aporte como SysAdmin?',
 '["Coordina con datos concretos", "Comunica avances"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Trabajar en silencio sin decir nada hasta tener la solución final"},
    {"id":"B","texto":"Compartir métricas y logs relevantes, proponer hipótesis y comunicar claramente las acciones que estás realizando"},
    {"id":"C","texto":"Esperar a que otro equipo resuelva porque es más rápido"},
    {"id":"D","texto":"Buscar culpables en lugar de soluciones"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'sr', 'SysAdmin', 'abierta',
 'Describe una situación en la que tuviste que mantener la continuidad operativa de una infraestructura crítica (por ejemplo, durante un cambio, corte o falla). ¿Cómo organizaste al equipo y qué resultados lograste?',
 '["Piensa en continuidad operativa", "Incluye decisiones que tomaste tú"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Desarrollador Backend
('BL', 'Desarrollador', 'jr', 'Desarrollador Backend', 'opcion_multiple',
 'Estás trabajando remoto y detectas que tu implementación impactará a otro servicio backend. ¿Qué haces?',
 '["Comunica antes de romper cosas", "Trabajo en equipo"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Hacer el cambio sin avisar y ver qué pasa"},
    {"id":"B","texto":"Avisar al otro desarrollador, coordinar el cambio y acordar pruebas de integración"},
    {"id":"C","texto":"Esperar a que el otro equipo encuentre el problema"},
    {"id":"D","texto":"Cancelar el cambio sin informar"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Desarrollador', 'jr', 'Desarrollador Backend', 'abierta',
 'Cuenta una vez en la que pediste ayuda para resolver un bug complejo en backend. ¿Cómo lo abordaste y qué aprendiste?',
 '["Piensa en un bug real", "Incluye qué cambiaste después de esa experiencia"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Desarrollador', 'mid', 'Desarrollador Backend', 'opcion_multiple',
 'QA reporta un bug crítico en una API que tú desarrollaste, cerca de una entrega. ¿Cuál es tu mejor reacción?',
 '["Calidad y colaboración", "No se trata de culpar"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Decir que “en tu máquina funciona” y cerrar el bug"},
    {"id":"B","texto":"Revisar el caso con QA, reproducir el problema, analizar la causa y proponer una solución con su impacto"},
    {"id":"C","texto":"Ignorar el bug porque llega tarde"},
    {"id":"D","texto":"Pedir que negocio lo acepte tal cual sin informar el riesgo"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Desarrollador', 'sr', 'Desarrollador Backend', 'abierta',
 'Describe una experiencia en la que lideraste la mejora de la calidad del backend (por ejemplo, pruebas, revisión de código o refactor). ¿Qué problema resolviste y qué impacto tuvo en el equipo?',
 '["Piensa en una mejora concreta", "Cuenta antes y después"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Desarrollador Frontend
('BL', 'Desarrollador', 'jr', 'Desarrollador Frontend', 'opcion_multiple',
 'El equipo de diseño te entrega una maqueta que en móvil se ve poco usable. ¿Qué haces?',
 '["Trabajo con diseño", "No cambies todo solo"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Implementar igual la maqueta aunque sepas que será incómoda"},
    {"id":"B","texto":"Modificar todo por tu cuenta sin avisar a diseño"},
    {"id":"C","texto":"Pedir una reunión breve, mostrar ejemplos del problema en móvil y proponer ajustes a la maqueta"},
    {"id":"D","texto":"Rechazar la maqueta sin dar detalles"}
  ], "respuesta_correcta":"C"}'::jsonb
),

('BL', 'Desarrollador', 'jr', 'Desarrollador Frontend', 'abierta',
 'Cuenta una situación en la que tuviste que ajustar una interfaz según comentarios de usuarios o diseño. ¿Qué cambiaste y qué resultado obtuviste?',
 '["Piensa en feedback real", "Describe el cambio y su efecto"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Desarrollador', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
 'Trabajas con un desarrollador backend y surgen problemas por mal entendimiento de los contratos de la API. ¿Qué acción es más efectiva?',
 '["Comunicación y acuerdos claros"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Seguir asumiendo cómo funciona la API y corregir sobre la marcha"},
    {"id":"B","texto":"Definir en conjunto el contrato (request/response), documentarlo y adaptar el código de ambos lados"},
    {"id":"C","texto":"Pedir que el backend se adapte solo a lo que tú necesitas"},
    {"id":"D","texto":"Dejar de hablar con el otro desarrollador"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Desarrollador', 'sr', 'Desarrollador Frontend', 'abierta',
 'Describe una vez en la que lideraste la mejora de la experiencia de usuario (UX) en un producto o módulo. ¿Qué problema detectaste y cómo se vio el impacto en los usuarios?',
 '["Piensa en una mejora de UX", "Menciona datos o señales del impacto si puedes"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Desarrollador Fullstack
('BL', 'Desarrollador', 'jr', 'Desarrollador Fullstack', 'opcion_multiple',
 'En un sprint te asignan tareas de frontend y backend. ¿Cómo organizas tu trabajo?',
 '["Piensa en dependencias y comunicación"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Hacer un poco de cada cosa sin terminar nada"},
    {"id":"B","texto":"Revisar dependencias, acordar prioridades con el equipo y avanzar en bloques terminando tareas completas"},
    {"id":"C","texto":"Hacer solo las tareas que más te gustan"},
    {"id":"D","texto":"Esperar a que el Scrum Master te diga exactamente qué hacer"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Desarrollador', 'jr', 'Desarrollador Fullstack', 'abierta',
 'Cuenta una experiencia en la que tuviste que aprender algo nuevo (por ejemplo, una tecnología de frontend o backend) para sacar adelante una tarea. ¿Cómo lo hiciste?',
 '["Piensa en un aprendizaje concreto", "Explica cómo te organizaste para aprender"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Desarrollador', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 'Estás en medio de un desarrollo y negocio cambia prioridades del sprint. ¿Qué haces?',
 '["Piensa en adaptación y comunicación con el equipo"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Ignorar el cambio y terminar lo que estabas haciendo"},
    {"id":"B","texto":"Revisar con el equipo el impacto del cambio, reordenar el trabajo y comunicar qué quedará dentro o fuera del sprint"},
    {"id":"C","texto":"Aceptar el cambio pero sin modificar el plan"},
    {"id":"D","texto":"Decir que el cambio es imposible sin analizarlo"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Desarrollador', 'sr', 'Desarrollador Fullstack', 'abierta',
 'Describe un caso en el que ayudaste al equipo a mejorar la colaboración entre frontend, backend y DevOps. ¿Qué hiciste para alinear a todos?',
 '["Piensa en un caso real", "Incluye reuniones, acuerdos o cambios de proceso que impulsaste"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Analista de Datos
('BL', 'TI', 'jr', 'Analista de Datos', 'opcion_multiple',
 'Te piden un informe “para hoy” pero no está claro qué decisión se tomará con esos datos. ¿Qué haces?',
 '["Piensa en entender el objetivo", "No es solo hacer gráficos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Generar muchos gráficos y esperar que alguno sirva"},
    {"id":"B","texto":"Hacer algunas preguntas breves para entender qué decisión quieren tomar y enfocar el análisis en eso"},
    {"id":"C","texto":"Negarte a hacer el informe"},
    {"id":"D","texto":"Enviar solo la tabla de datos sin comentarios"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'jr', 'Analista de Datos', 'abierta',
 'Cuenta una ocasión en la que detectaste un problema en la calidad de los datos (por ejemplo, duplicados o inconsistencias). ¿Cómo lo manejaste?',
 '["Piensa en un caso real", "Incluye a quién avisaste y qué se hizo"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'TI', 'mid', 'Analista de Datos', 'opcion_multiple',
 'Detectas inconsistencias importantes en las fuentes de datos de un dashboard clave. ¿Cuál es la mejor acción?',
 '["Calidad de datos primero", "Comunica el riesgo"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Ignorarlas porque el dashboard ya está en producción"},
    {"id":"B","texto":"Documentar las inconsistencias, informar a los dueños de datos y proponer pasos para corregirlas"},
    {"id":"C","texto":"Eliminar los datos problemáticos sin avisar"},
    {"id":"D","texto":"Cambiar las métricas para que no se note"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'sr', 'Analista de Datos', 'abierta',
 'Describe una experiencia en la que un análisis tuyo generó un impacto importante (por ejemplo, cambio de estrategia o mejora de un proceso). ¿Qué descubriste y qué se hizo con esa información?',
 '["Piensa en un caso con impacto", "Cuenta qué decisión cambió gracias al análisis"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Analista de Negocios
('BL', 'Administracion', 'jr', 'Analista de Negocios', 'opcion_multiple',
 'Durante una reunión, distintas áreas usan nombres distintos para el mismo indicador. ¿Qué haces?',
 '["Piensa en claridad y acuerdos", "Glosario común"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Anotar todo tal cual y dejar que cada área use su nombre"},
    {"id":"B","texto":"Definir en conjunto un nombre y descripción, documentarlo y validarlo con todos"},
    {"id":"C","texto":"Elegir tú un nombre sin consultar"},
    {"id":"D","texto":"Suspender la reunión y no retomar el tema"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'jr', 'Analista de Negocios', 'abierta',
 'Cuenta una ocasión en la que ayudaste a un área a entender mejor sus indicadores o reportes. ¿Qué hiciste para explicarlos?',
 '["Piensa en una explicación que diste", "Incluye cómo adaptaste el lenguaje"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Administracion', 'mid', 'Analista de Negocios', 'opcion_multiple',
 'Distintas áreas (ventas, operaciones, finanzas) tienen prioridades distintas para un mismo proyecto. ¿Cuál es tu mejor rol?',
 '["Gestión de stakeholders", "Buscar alineamiento"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Apoyar solo a la que tenga más poder"},
    {"id":"B","texto":"Facilitar una conversación para alinear objetivos, definir criterios en común y documentar acuerdos"},
    {"id":"C","texto":"Hacer un informe distinto para cada área sin buscar un mínimo común"},
    {"id":"D","texto":"No involucrarte en el conflicto"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'sr', 'Analista de Negocios', 'abierta',
 'Describe una experiencia en la que tu análisis ayudó a la gerencia a tomar una decisión crítica (por ejemplo, cambio de producto, inversión o reducción de costos). ¿Cómo lo presentaste?',
 '["Piensa en una decisión importante", "Incluye cómo comunicaste los hallazgos"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Analista QA
('BL', 'TI', 'jr', 'Analista QA', 'opcion_multiple',
 'En una daily, desarrollo y negocio no se ponen de acuerdo sobre la prioridad de un defecto. ¿Qué puedes aportar como QA?',
 '["Piensa en riesgo y evidencias"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"No decir nada para no entrar en conflicto"},
    {"id":"B","texto":"Aportar datos sobre el impacto del defecto, ejemplos de uso y ayudar a estimar el riesgo para decidir su prioridad"},
    {"id":"C","texto":"Decir que todos los defectos son críticos siempre"},
    {"id":"D","texto":"Apoyar automáticamente al que hable más fuerte"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'jr', 'Analista QA', 'abierta',
 'Cuenta una ocasión en la que detectaste un problema importante antes de que llegara a producción. ¿Cómo lo comunicaste al equipo?',
 '["Piensa en un bug real o un riesgo", "Incluye la reacción del equipo"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'TI', 'mid', 'Analista QA', 'opcion_multiple',
 'Ves que el mismo tipo de defecto se repite en varios releases. ¿Qué deberías impulsar?',
 '["Mejora continua", "No solo reportar otra vez"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Seguir reportando el mismo defecto cada vez"},
    {"id":"B","texto":"Proponer un análisis de causa raíz y ajustar pruebas, criterios de aceptación o proceso"},
    {"id":"C","texto":"Dejar de reportarlo porque es repetitivo"},
    {"id":"D","texto":"Pedir más tiempo sin cambiar nada del proceso"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'sr', 'Analista QA', 'abierta',
 'Describe una experiencia en la que ayudaste a mejorar la cultura de calidad en tu equipo o empresa. ¿Qué hiciste diferente?',
 '["Piensa en cambios de prácticas, reuniones o métricas", "Cuenta el impacto en el equipo"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Analista Funcional
('BL', 'TI', 'jr', 'Analista Funcional', 'opcion_multiple',
 'Durante el levantamiento de requerimientos, los usuarios usan distintos términos para lo mismo. ¿Qué haces?',
 '["Piensa en claridad de lenguaje", "Glosario compartido"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Anotar todo tal cual y dejar que desarrollo interprete"},
    {"id":"B","texto":"Crear y validar con ellos un glosario común con términos y definiciones claras"},
    {"id":"C","texto":"Elegir tú los nombres sin consultar"},
    {"id":"D","texto":"Terminar la reunión y no retomar el tema"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'jr', 'Analista Funcional', 'abierta',
 'Cuenta una situación en la que tuviste que explicar un proceso o requisito complejo a alguien no técnico. ¿Cómo lo hiciste?',
 '["Piensa en un caso real", "Incluye ejemplos o apoyos visuales si los usaste"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 'En un proyecto con alta presión, te piden recortar documentación de análisis. ¿Qué propones?',
 '["Documentación mínima pero útil"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Eliminar toda la documentación para ganar tiempo"},
    {"id":"B","texto":"Acordar con el equipo un set mínimo (flujos críticos, reglas clave, criterios de aceptación) y mantener al menos eso"},
    {"id":"C","texto":"Negarte a avanzar sin documentar todo en detalle"},
    {"id":"D","texto":"Documentar solo en tus notas personales"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'TI', 'sr', 'Analista Funcional', 'abierta',
 'Describe una experiencia en la que ayudaste a alinear a negocio, desarrollo y QA en torno al alcance de un proyecto complejo. ¿Cómo evitaste el “scope creep”?',
 '["Piensa en un proyecto real", "Incluye acuerdos y mecanismos de control que usaste"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Asistente Administrativo
('BL', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 'Tu jefe te pide un informe “para ahora ya”, pero ya tienes otras tareas comprometidas para el día. ¿Qué haces?',
 '["Piensa en gestión del tiempo y comunicación"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Intentar hacerlo todo sin avisar si algo no se cumple"},
    {"id":"B","texto":"Explicar tu carga actual, pedir priorizar tareas y reorganizar tu día en base a eso"},
    {"id":"C","texto":"Decir que no harás el informe porque estás ocupado"},
    {"id":"D","texto":"Ignorar las otras tareas y hacer solo el informe"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'jr', 'Asistente Administrativo', 'abierta',
 'Cuenta una ocasión en la que debiste organizar muchas tareas al mismo tiempo en la oficina. ¿Cómo decidiste por dónde empezar?',
 '["Piensa en un día ajetreado", "Incluye cómo priorizaste"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Administracion', 'mid', 'Asistente Administrativo', 'opcion_multiple',
 'Notas un error en un documento que ya fue enviado a un cliente. ¿Cuál es la mejor acción?',
 '["Piensa en responsabilidad y relación con el cliente"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"No decir nada para evitar problemas"},
    {"id":"B","texto":"Informar a tu jefe, proponer corregir el documento y enviar una versión actualizada si es necesario"},
    {"id":"C","texto":"Echarle la culpa a otra persona"},
    {"id":"D","texto":"Eliminar el documento del archivo y olvidarlo"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'sr', 'Asistente Administrativo', 'abierta',
 'Describe una experiencia en la que apoyaste a tu equipo o jefatura en un periodo de alta carga de trabajo (por ejemplo, cierre de mes o evento importante). ¿Qué hiciste para que todo saliera adelante?',
 '["Piensa en un periodo de alta presión", "Incluye cómo ayudaste a organizar al equipo"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Analista Contable
('BL', 'Administracion', 'jr', 'Analista Contable', 'opcion_multiple',
 'Durante el registro de facturas encuentras un monto que no cuadra con el documento enviado. ¿Qué haces?',
 '["Piensa en exactitud y comunicación"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Ajustar el monto para que cuadre y seguir"},
    {"id":"B","texto":"Revisar el documento, consultar la diferencia con quien corresponda y registrar correctamente el valor"},
    {"id":"C","texto":"Ignorar el problema porque el monto es pequeño"},
    {"id":"D","texto":"Registrar cualquier valor y corregir después si alguien reclama"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'jr', 'Analista Contable', 'abierta',
 'Cuenta una situación en la que detectaste un error contable o administrativo. ¿Cómo lo corregiste?',
 '["Piensa en un error real", "Incluye qué hiciste para evitar que volviera a ocurrir"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 'Durante el cierre contable, descubres una diferencia que no puedes explicar rápidamente. El plazo para entregar los estados es corto. ¿Qué haces?',
 '["Piensa en ética y tiempos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Ajustar la cifra para que cuadre sin investigar"},
    {"id":"B","texto":"Informar la diferencia, investigar lo posible y acordar un plan para terminar el análisis si no alcanzas"},
    {"id":"C","texto":"Retrasar la entrega sin informar a nadie"},
    {"id":"D","texto":"Eliminar la cuenta con diferencia del estado financiero"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'sr', 'Analista Contable', 'abierta',
 'Describe una experiencia en la que tuviste que explicar información contable compleja a alguien sin conocimientos financieros (por ejemplo, un gerente o cliente). ¿Cómo lo hiciste comprensible?',
 '["Piensa en una explicación importante", "Incluye ejemplos o metáforas si las usaste"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
),

-- SOFT SKILLS - Encargado de Administración
('BL', 'Administracion', 'jr', 'Encargado de Administración', 'opcion_multiple',
 'Debes mantener orden físico y digital de documentación legal y laboral. Notas que varios documentos no están actualizados. ¿Qué haces?',
 '["Piensa en orden y proactividad"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Dejar los documentos como están para no generar trabajo extra"},
    {"id":"B","texto":"Hacer un inventario, priorizar qué actualizar y proponer un plan para regularizar la documentación"},
    {"id":"C","texto":"Eliminar los documentos antiguos sin revisar su importancia"},
    {"id":"D","texto":"Esperar a que el directorio pida algo específico para recién ordenar"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'jr', 'Encargado de Administración', 'abierta',
 'Cuenta una ocasión en la que organizaste o mejoraste el orden de documentos o procesos administrativos en tu trabajo o estudios. ¿Qué cambió con tu mejora?',
 '["Piensa en un cambio concreto", "Incluye antes y después"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb
),

('BL', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 'Debes informar al Directorio sobre una desviación importante en el presupuesto. ¿Qué es lo más adecuado?',
 '["Transparencia con propuesta de acción"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Ocultar la desviación para evitar preguntas difíciles"},
    {"id":"B","texto":"Presentar la desviación con datos claros, explicar las causas y proponer acciones para corregirla"},
    {"id":"C","texto":"Mencionar solo los resultados positivos y omitir los negativos"},
    {"id":"D","texto":"Culpar a otra área sin mostrar información"}
  ], "respuesta_correcta":"B"}'::jsonb
),

('BL', 'Administracion', 'sr', 'Encargado de Administración', 'abierta',
 'Describe una experiencia en la que tuviste que liderar al equipo administrativo en un periodo de alta presión (por ejemplo, auditoría, cierre de año o cambio importante). ¿Cómo lo manejaste?',
 '["Piensa en un momento crítico", "Cuenta cómo apoyaste al equipo y qué resultados obtuvieron"]'::jsonb,
 '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb
);


COMMIT;

-- =============================================================================
-- INSERT REQUISITOS POR CARGO (skills_cargo)
-- =============================================================================
INSERT INTO skills_cargo (cargo, tipo, descripcion) VALUES
('Soporte TI','tecnico','Prestar apoyo a los Asistentes de reuniones para proyectar presentaciones'),
('Soporte TI','tecnico','Documentación: Mantener registros detallados de los procedimientos y servicios prestados, incluyendo manuales y registros de resolución de problemas'),
('Soporte TI','blando','Buenas habilidades comunicacionales y orientación al cliente'),
('Soporte TI','blando','Habilidades personales: autonomía, dinamismo, iniciativa, responsabilidad y orientación a la resolución de problemas'),
('Soporte TI','blando','Excelentes habilidades de comunicación y atención al usuario'),
('Soporte TI','blando','Brindar soporte técnico en sitio y remoto a los equipos informáticos, software y redes de la empresa, garantizando la resolución de problemas técnicos, la ejecución de mantenimiento preventivo y correctivo, y el asesoramiento técnico para el óptimo funcionamiento de los sistemas'),
('Soporte TI','blando','Diagnóstico y resolución de problemas: Identificar y solucionar problemas técnicos críticos que afecten la continuidad de las operaciones'),
('Soporte TI','blando','Colaboración con el equipo de TI: Trabajar de forma conjunta con otros miembros del área para resolver problemas complejos y asegurar la alineación de objetivos'),
('Soporte TI','blando','Formación técnica en áreas relacionadas con informática, redes, telecomunicaciones o similar'),
('DevOps Engineer','tecnico','Nos encontramos en búsqueda de un(a) DevOps / Cloud Engineer para el área TI, buscamos un perfil con fuertes habilidades técnicas en infraestructura y GCP, con experiencia previa en compañías similares e idealmente habiendo liderado o participado en procesos de implementación de servicios en la nube'),
('DevOps Engineer','tecnico','Diseñar, implementar y mantener infraestructura en la nube (GCP)'),
('DevOps Engineer','tecnico','Implementar y administrar clústeres y contenedores con Docker y Kubernetes (GKE)'),
('DevOps Engineer','tecnico','Desarrollar y mantener pipelines CI/CD con GitLab (runners, stages, jobs)'),
('DevOps Engineer','tecnico','Amplia experiencia en Google Cloud Platform (GCP)'),
('DevOps Engineer','tecnico','Experiencia administrando infraestructura en la nube y entornos Linux'),
('DevOps Engineer','tecnico','Sólidos conocimientos en Docker y Kubernetes (GKE, EKS, Helm Charts)'),
('DevOps Engineer','tecnico','Experiencia comprobada en pipelines de CI/CD utilizando GitLab CI/CD, GitHub Actions o Jenkins'),
('DevOps Engineer','tecnico','Dominio de configuración de pipelines CI/CD con GitLab y uso de Templating Engines'),
('DevOps Engineer','tecnico','Familiaridad con Apache Kafka y arquitecturas basadas en microservicios'),
('DevOps Engineer','blando','Capacidad de resolución de problemas y pensamiento analítico aplicado a procesos de automatización'),
('DevOps Engineer','blando','Comunicación efectiva'),
('SysAdmin','tecnico','En Tecnocomp iniciamos el proceso para incorporar a un Administrador de Sistemas que prestará servicios presenciales a un importante cliente del sector energía en la Región Metropolitana'),
('SysAdmin','tecnico','Conocimientos en herramientas de respaldo y uso de PowerShell'),
('SysAdmin','tecnico','Deseable: experiencia con Linux, Azure, y certificaciones (Microsoft, VMware, CompTIA, ITIL)'),
('SysAdmin','tecnico','En este rol, te incorporarás a un equipo orientado al soporte y la administración de infraestructuras críticas, participando en proyectos innovadores para clientes de alto nivel y con un compromiso claro de excelencia operativa y customersociedad digital'),
('SysAdmin','tecnico','Supervisar y dar soporte a plataformas y experiencias digitales'),
('SysAdmin','tecnico','Es deseable experiencia en Kubernetes y/o Docker Swarm (en entornos on'),
('SysAdmin','tecnico','premise o en la nube: GCP, AWS, Azure), así como familiaridad con herramientas de monitoreo (ELK, Datadog, AppDynamics)'),
('SysAdmin','tecnico','Es fundamental experiencia con bases de datos SQL y NoSQL (por ejemplo Cassandra, MongoDB) y manejo de herramientas de ticketing (Jira)'),
('SysAdmin','tecnico','En BICE Vida nos encontramos en búsqueda de un Ingeniero SysAdmin, quien estará encargado de mantener la continuidad operativa y mejorar los servicios de infraestructura tecnológica alojadas en ambientes on premise, Amazon Web Services, Microsoft Azure y cualquier otro prestador de Servicios de Infraestructura, velando por contar con un ambiente estable y seguro'),
('SysAdmin','tecnico','Conocimiento y experiência en: administración y soporte de plataformas Linux, Windows; administración de plataformas de Sistemas Operativos, Virtualización, storage; y en la administración de recursos de infraestructura, servidores físicos y virtuales, storage, networking'),
('SysAdmin','blando','Comunicación clara y trabajo colaborativo'),
('SysAdmin','blando','Liderar mesas de incidentes y participar activamente en la resolución de problemas'),
('SysAdmin','blando','Se valorará certificaciones relacionadas con sistemas, nube y contenedores, así como habilidades de trabajo en equipos ágiles y conocimiento de metodologías de ITIL o similares'),
('SysAdmin','blando','La modalidad híbrida que ofrecemos, ubicada en Las Condes, permite combinar la flexibilidad del trabajo remoto con la colaboración presencial, facilitando un mejor equilibrio y dinamismo laboral'),
('SysAdmin','blando','Trabajo en equipo, pensamiento analítico, sentido de urgencia, orientación al cliente interno, proactividad y autogestión'),
('SysAdmin','blando','Alta capacidad analítica, orientación al cliente, trabajo colaborativo y comunicación efectiva'),
('SysAdmin','blando','Horario: Artículo 22 (colaboración con distintos mercados: Chile, Perú, México y Colombia)'),
('SysAdmin','blando','Registrar y tratar proactivamente los incidentes y requerimientos asociados al área de Operaciones y Tecnologías'),
('Desarrollador Backend','tecnico','Gestión de Spring Boot'),
('Desarrollador Backend','tecnico','Práctica en JUnit, Mockito y Hamcrest'),
('Desarrollador Backend','tecnico','Creación de servicios REST y SOAP'),
('Desarrollador Backend','tecnico','Aplicación de APIs con estándares modernos'),
('Desarrollador Backend','tecnico','Digital library'),
('Desarrollador Backend','tecnico','Access to digital books or subscriptions'),
('Desarrollador Backend','tecnico','Participar en la integración de APIs internas y externas'),
('Desarrollador Backend','tecnico','Experiencia en integración con APIs internas y externa (comprobable)'),
('Desarrollador Backend','tecnico','PHP, JavaScript, MySQL o PostgreSQL'),
('Desarrollador Backend','tecnico','Integración con APIs REST y estructuras JSON'),
('Desarrollador Backend','blando','Nos guiamos por valores como el trabajo en equipo, la confiabilidad, la empatía, el compromiso, la honestidad y la calidad, porque sabemos que los buenos resultados parten de buenas relaciones'),
('Desarrollador Backend','blando','Mantener comunicación fluida con otros desarrolladores y áreas de soporte'),
('Desarrollador Backend','blando','Nuestros empleados trabajan remotamente, pero lo hacen dentro de una cultura confiable y sólida que promueve diversidad y trabajo en equipo'),
('Desarrollador Backend','blando','Comunicación efectiva para interactuar con usuarios y equipos'),
('Desarrollador Backend','blando','Trabajo en equipo y actitud colaborativa'),
('Desarrollador Backend','blando','Proactividad en la resolución de problemas'),
('Desarrollador Frontend','tecnico','Un importante canal de televisión está en búsqueda de un(a) Desarrollador(a) de Plataformas para integrarse al área digital y de Prensa'),
('Desarrollador Frontend','tecnico','End, con conocimientos en HTML, CSS y Java/JavaScript'),
('Desarrollador Frontend','tecnico','Familiaridad con los sistemas de control de versiones (por ejemplo, Git)'),
('Desarrollador Frontend','tecnico','Integración con APIs Rest desde el front'),
('Desarrollador Frontend','tecnico','Sí, sabemos que recibís un montón de ofertas de trabajo y que podéis pensar que esta es una más de ellas, que poco o nada nos diferencia del resto de empresas, pero no, os prometemos que esta oferta es muy pero que muy diferente (pero sobre todo muy muy TOP!)🤞🏻'),
('Desarrollador Frontend','tecnico','Es decir, sabemos cuándo y cómo usar React, TypeScript o Svelte, pero para ellos tenemos que conocer a la perfección HTML, CSS y JavaScript'),
('Desarrollador Frontend','tecnico','Necesitamos que conozcas los fundamentos de HTML, CSS y JavaScript, que son la base de nuestro trabajo'),
('Desarrollador Frontend','tecnico','Experiencia trabajando en equipo con Git'),
('Desarrollador Frontend','tecnico','Que seas capaz de construir herramientas que nos hagan trabajar mejor: CLI, Github Actions, extensiones de navegador, etc'),
('Desarrollador Frontend','tecnico','Tienes conocimiento y has trabajado con CDNs y servicios en la nube (AWS, GCP y Azure)'),
('Desarrollador Frontend','blando','¿Eres apasionado por el desarrollo Front End, proactivo y siempre dispuesto a aprender? ¡Esta oportunidad es para ti! Estamos en busca de perfiles senior que quieran formar parte de un equipo innovador'),
('Desarrollador Frontend','blando','Excelentes habilidades de comunicación y resolución de problemas'),
('Desarrollador Frontend','blando','Trabaja en estrecha colaboración con el equipo de diseño y los desarrolladores de back'),
('Desarrollador Frontend','blando','Fuertes habilidades de resolución de problemas y atención al detalle'),
('Desarrollador Frontend','blando','Excelentes habilidades de comunicación y capacidad para entender los requisitos y expectativas del cliente y del usuario final'),
('Desarrollador Fullstack','tecnico','Desarrollar módulos, microservicios, mejoras de API y aplicaciones como parte de la mejora continua de los productos propietarios de la compañía'),
('Desarrollador Fullstack','tecnico','Dominio de Python para desarrollo backend, con experiencia específica en Flask (conocimiento en Django o FastAPI es un plus)'),
('Desarrollador Fullstack','tecnico','js y ecosistema frontend contemporáneo (HTML5, CSS3, JavaScript ES6+)'),
('Desarrollador Fullstack','tecnico','Manejo avanzado de Git y flujos de trabajo colaborativo en GitHub'),
('Desarrollador Fullstack','tecnico','Experiencia en línea de comandos de Linux'),
('Desarrollador Fullstack','tecnico','Conocimiento de MySQL y manejo de SQLAlchemy como ORM'),
('Desarrollador Fullstack','tecnico','Nociones básicas de contenedores (Docker)'),
('Desarrollador Fullstack','tecnico','Estamos en búsqueda de un Desarrollador Full Stack apasionado por la tecnología, la innovación y la creación de soluciones robustas para un futuro digital Si tienes experiencia en desarrollo de software, estás familiarizado con las últimas herramientas y deseas trabajar en un ambiente ágil, ¡te estamos buscando! […]'),
('Desarrollador Fullstack','tecnico','Alto conocimiento de Java J2EE y Java Spring Boot […]'),
('Desarrollador Fullstack','tecnico','Alto conocimiento Serverless computing AWS (NodeJs, lambda, DynamoDB) […]'),
('QA Automation','tecnico','performing team!If you are an QA Automation ambitious and passionate about innovation, joining Yuno will allow you to transform your passion into real high'),
('QA Automation','tecnico','As a QA Automation you will be part of the team of integrations'),
('QA Automation','tecnico','Create and manage test cases for regression; create automation and performance testing'),
('QA Automation','tecnico','Estimate, prioritize, plan, setup test environment, and conduct testing activities'),
('QA Automation','tecnico','Perform thorough regression testing'),
('QA Automation','tecnico','standard testing frameworks and tools'),
('QA Automation','tecnico','Identify test scenarios and use cases for automation, considering various payment methods and scenarios'),
('QA Automation','tecnico','Proven experience as a QA Automation Engineer or similar role in the payments industry'),
('QA Automation','tecnico','Demonstrated knowledge in: Automation backend: Python, Cucumber/Behave, Automation web/mobile, Typescrip, Webdriver'),
('QA Automation','tecnico','POO, design patterns, docker, k6/jmeter, CI/CD tools and y monitoring tools such as DataDog'),
('Analista de Datos','tecnico','SQL Server Integration Services (SSIS)'),
('Analista de Datos','tecnico','SQL Server Analysis Services (SSAS)'),
('Analista de Datos','tecnico','Programación (Python, SQL, RPA)'),
('Analista de Datos','tecnico','Diseñar, optimizar y ejecutar consultas SQL (MySQL y SQL Server) para extracción y transformación de datos'),
('Analista de Datos','tecnico','Dominio avanzado de SQL (consultas, procedimientos almacenados, funciones, índices) en MySQL y SQL Server'),
('Analista de Datos','blando','Valoramos a personas analíticas, proactivas y con capacidad para aportar ideas que generen impacto'),
('Analista de Datos','blando','Buscamos a una persona analítica, proactiva y orientada al detalle'),
('Analista de Datos','blando','Pensamiento analítico, orientación al detalle y capacidad para identificar patrones en grandes volúmenes de datos'),
('Analista de Negocios','tecnico','Conocimientos de SQL para validación de datos y análisis económico'),
('Analista de Negocios','tecnico','Formar parte del equipo estratégico detrás de la optimización de procesos críticos de operaciones de Capitaria, asegurando que cada decisión se base en datos relevantes y generando mejoras continuas en los mismos'),
('Analista de Negocios','tecnico','Monitoreo de KPIs Financieros y Operacionales >Diseñar y mantener dashboards de indicadores clave relacionados con el uso de capital, márgenes operacionales, flujos de caja, entre otros'),
('Analista de Negocios','tecnico','Conocimiento de SQL, Python, y herramientas de visualización (Power BI, Tableau u otro)'),
('Analista de Negocios','blando','Generar base de datos y reportes que colaboran a la transparencia y comunicación interna'),
('Analista de Negocios','blando','¿Te apasiona el análisis, el trabajo en equipo y el contacto'),
('Analista de Negocios','blando','· Capacidad de análisis, comunicación efectiva y'),
('Analista de Negocios','blando','· Buen ambiente laboral y cultura de colaboración'),
('Analista de Negocios','blando','Este rol reportará directamente al Gerente General y trabajará en estrecha colaboración con el Director Ejecutivo que asesora el área de Finanzas y Mesa de Dinero'),
('Analista QA','tecnico','Analista Testing QA'),
('Analista QA','tecnico','Buscamos un QA Funcional con experiencia en el sector bancario y sólidos conocimientos en testing de software, metodologías ágiles y herramientas de gestión de calidad'),
('Analista QA','tecnico','Automatización y Mejora Continua: Automatizar pruebas de regresión utilizando Selenium / Cucumber / Gherkin'),
('Analista QA','tecnico','Experiencia en Testing de Software bajo metodologías ágiles (Scrum)'),
('Analista QA','tecnico','Conocimientos en pruebas manuales funcionales y de servicios (API, logs, base de datos)'),
('Analista QA','tecnico','Familiaridad con herramientas de automatización (Selenium, UFT, Appium) y frameworks BDD (Cucumber, Gherkin)'),
('Analista QA','tecnico','Conocimiento básico en testing en Cloud (AWS, OCI) y uso de granjas de dispositivos web y móviles'),
('Analista QA','tecnico','Deseable experiencia en herramientas de stress y performance testing (JMeter, LoadRunner)'),
('Analista QA','tecnico','Experiencia en QA de SQL, Shell, Control'),
('Analista QA','tecnico','Conocimiento a nivel de usuario en lenguaje PL/SQL y Unix'),
('Analista QA','blando','Colaboración en el Ciclo de Desarrollo: Participar en ceremonias ágiles y revisiones funcionales'),
('Analista QA','blando','Enfoque en la calidad y trabajo en equipo'),
('Analista QA','blando','Comunicación con el cliente y con su equipo de trabajo'),
('Analista QA','blando','Comunicación clara: capaz de traducir necesidades del negocio bancario a soluciones técnicas'),
('Analista QA','blando','Trabajo en equipo multidisciplinario: interacción con BAs, arquitectos, reguladores y áreas de riesgo'),
('Analista Funcional','tecnico','Ejecutar testing, levantar alertas y aplicar correctivos para optimizar las iniciativas implementadas'),
('Analista Funcional','tecnico','Experiencia con herramientas de desarrollo en plataformas abiertas (SQL Server, ambientes Windows)'),
('Analista Funcional','tecnico','Lenguaje: Java, Angular, Springboot'),
('Analista Funcional','tecnico','Versionamiento: bitbucket, gitlab'),
('Analista Funcional','tecnico','Base de datos: SQL server, mysql, postgresql'),
('Analista Funcional','tecnico','Manejo de procesos de QA, testing funcional y validación de integraciones'),
('Analista Funcional','tecnico','Conocimientos básicos de SQL para validaciones de datos'),
('Analista Funcional','tecnico','Experiencia en integración continua (Jenkins, GitLab CI/CD)'),
('Analista Funcional','blando','Manejo comunicacional ejecutivo y capacidad de relacionamiento transversal'),
('Analista Funcional','blando','Alta autonomía y proactividad'),
('Analista Funcional','blando','Manejo comunicacional ejecutivo y alta autonomía'),
('Asistente Administrativo','blando','Estamos buscando un Asistente Administrativo proactivo y organizado para unirse a nuestro equipo de Recursos Humanos'),
('Asistente Administrativo','blando','El candidato ideal será responsable, comprometido y poseerá excelentes habilidades comunicacionales y disposición para el trabajo en equipo'),
('Asistente Administrativo','blando','Buenas habilidades comunicacionales y disposición para el trabajo en equipo'),
('Asistente Administrativo','blando','Estamos buscando un/a Asistente Administrativo/a dinámico/a y proactivo/a para unirse a nuestro equipo'),
('Asistente Administrativo','blando','Buscamos a alguien con excelentes habilidades de organización, comunicación y capacidad para trabajar en equipo'),
('Analista Contable','tecnico','*Realizar la digitación de las facturas al sistema contable, para chequear por errores antes de imprimir los reportes'),
('Analista Contable','blando','Apoyo administrativo y comunicación con clientes y proveedores — Atender requerimientos administrativos relacionados con facturación, órdenes de compra y coordinación de pagos'),
('Encargado de Administración','tecnico','Informar mensualmente al Directorio sobre ejecución presupuestaria y preparar proyecciones financieras para el resto del año'),
('Encargado de Administración','tecnico','Bash, destacado holding de empresas con presencia a nivel nacional y ubicado en la zona norte de Santiago, busca incorporar a su equipo a un/a Jefe/a de Administración'),
('Encargado de Administración','tecnico','️ Mantener orden físico y digital de documentación legal, tributaria y laboral'),
('Encargado de Administración','blando','Buena comunicación y trabajo en equipo'),
('Encargado de Administración','blando','Capacidad de liderazgo, gestión de equipos y habilidades comunicacionales'),
('Encargado de Administración','blando','Orientación al detalle, proactividad y capacidad de trabajo bajo presión'),
('Encargado de Administración','blando','Comunicación Efectiva: Habilidad para transmitir información clara y precisa tanto a equipos internos como externos'),
('Encargado de Administración','blando','Resolución de Problemas: Aptitud para identificar situaciones críticas y proponer soluciones oportunas');

COMMIT;

-- =============================================================================
-- 4. CONSENTIMIENTO INICIAL
-- =============================================================================
BEGIN;
INSERT INTO consentimiento_texto (version, titulo, cuerpo)
VALUES ('v1.0','Consentimiento de uso de datos','Texto completo del consentimiento que verán los usuarios.');
COMMIT;

-- =============================================================================
-- 5. CREACIÓN DE USUARIOS ADMIN
-- =============================================================================
BEGIN;
INSERT INTO usuario (correo, contrasena_hash, nombre, idioma, estado, rol) VALUES
(
    'admin@entrevista.com',
    '$argon2id$v=19$m=19456,t=2,p=1$ohYeqdkuF1wBlmYhTi5uow$p3mUFWphjPNNU4fVkbFL7IICdDJnB8bDlbFXoycJjOA',
    'Admin inicial',
    'es',
    'activo',
    'admin'
),
(
    'Prueba1@entrevista.com',
    '$argon2id$v=19$m=19456,t=2,p=1$ohYeqdkuF1wBlmYhTi5uow$p3mUFWphjPNNU4fVkbFL7IICdDJnB8bDlbFXoycJjOA',
    'Prueba1',
    'es',
    'activo',
    'user'
);
COMMIT;
