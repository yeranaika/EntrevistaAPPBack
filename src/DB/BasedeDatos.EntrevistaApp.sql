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
    idioma           VARCHAR(10)  NOT NULL DEFAULT 'es',
    estado           VARCHAR(19)  NOT NULL DEFAULT 'activo',
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rol              VARCHAR(10)  NOT NULL DEFAULT 'user',
    CONSTRAINT chk_email_format CHECK (correo ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT chk_usuario_rol CHECK (rol IN ('user','admin'))
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

-- 2) Suscripciones y pagos
CREATE TABLE suscripcion (
    suscripcion_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id       UUID NOT NULL REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    plan             VARCHAR(100)  NOT NULL DEFAULT 'free',
    proveedor        VARCHAR(50),
    estado           VARCHAR(20)  NOT NULL DEFAULT 'inactiva',
    fecha_inicio     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_renovacion TIMESTAMPTZ,
    fecha_expiracion TIMESTAMPTZ,
    CONSTRAINT chk_estado_suscripcion CHECK (estado IN ('activa','inactiva','cancelada','suspendida','vencida'))
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

CREATE TABLE pregunta (
    pregunta_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco       VARCHAR(5),      -- 'NV' (nivelación), 'PR' (práctica), etc.
    sector           VARCHAR(80),     -- área: 'TI', 'Administracion', etc.
    nivel            VARCHAR(3),      -- 'jr', 'ssr', 'sr'
    meta_cargo       VARCHAR(120),    -- cargo objetivo (opcional)
    tipo_pregunta    VARCHAR(20) NOT NULL DEFAULT 'opcion_multiple'
                     CHECK (tipo_pregunta IN ('opcion_multiple','abierta')),
    texto            TEXT NOT NULL,   -- enunciado
    pistas           JSONB,           -- hints / tags / explicaciones extra
    config_respuesta JSONB,           -- opciones y/o criterios de corrección
    activa           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- 4) Pruebas y relaciones
CREATE TABLE prueba (
    prueba_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_prueba VARCHAR(8)  NOT NULL DEFAULT 'aprendiz',
    area        VARCHAR(80),
    nivel       VARCHAR(3),
    metadata    VARCHAR(120),
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
-- 1. ANALISTA TI (Código: PR)
BEGIN;

-- 1. ANALISTA TI (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, texto, pistas, config_respuesta) VALUES
('PR', 'Analista TI', 'jr', 'Soporte TI','¿Qué es un Requisito Funcional?', '["Describe lo que el sistema debe hacer", "Comportamiento"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Cómo se ve el sistema"},{"id":"B", "texto":"Una función o servicio que el sistema debe proveer"},{"id":"C", "texto":"La velocidad del sistema"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'En un diagrama de flujo, ¿qué forma representa una decisión?', '["Tiene forma de diamante", "Suelen salir flechas de SI/NO"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Rectángulo"},{"id":"B", "texto":"Rombo/Diamante"},{"id":"C", "texto":"Círculo"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','¿Qué significan las siglas UML?', '["Lenguaje visual estándar", "Unified..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Universal Modeling List"},{"id":"B", "texto":"Unified Modeling Language"},{"id":"C", "texto":"User Management Logic"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','Define brevemente qué es un "Stakeholder".', '["Interesado", "Puede afectar o ser afectado"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','¿Cuál es el actor principal en un Caso de Uso de "Login"?', '["Quien inicia la acción", "Persona frente al PC"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Usuario"},{"id":"B", "texto":"Base de Datos"},{"id":"C", "texto":"Servidor"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','Diferencia principal entre Requisito Funcional y No Funcional.', '["El Qué vs el Cómo", "Calidad vs Comportamiento"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','En metodología Ágil, ¿quién suele priorizar el Backlog?', '["Representa al negocio", "Product..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Scrum Master"},{"id":"B", "texto":"Product Owner"},{"id":"C", "texto":"El Desarrollador"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','¿Qué es un "Bug"?', '["Error", "Fallo en el software"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 10, "max_caracteres": 150}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','¿Para qué sirve una entrevista de levantamiento de información?', '["Técnica de educción", "Hablar con el cliente"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Para programar el código"},{"id":"B", "texto":"Para entender las necesidades del usuario"},{"id":"C", "texto":"Para vender el producto"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'jr', 'Soporte TI','Menciona 3 técnicas para recopilar requisitos.', '["Entrevistas...", "Encuestas..."]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','Escribe el formato estándar de una Historia de Usuario.', '["Como [rol]...", "Quiero [acción]..."]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 200}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','¿Qué diagrama UML usarías para mostrar los estados por los que pasa una orden de compra?', '["Inicio, Pendiente, Aprobado, Fin", "Máquina de..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Diagrama de Clases"},{"id":"B", "texto":"Diagrama de Estados"},{"id":"C", "texto":"Diagrama de Despliegue"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','¿Qué es el criterio de aceptación?', '["Condiciones para dar por terminada una tarea", "Definition of Done"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','En BPMN, ¿qué representa un carril (Swimlane)?', '["Responsabilidad", "Actor o departamento"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Una decisión lógica"},{"id":"B", "texto":"Un actor o rol responsable de las tareas"},{"id":"C", "texto":"El flujo de datos"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','Explica qué es la Trazabilidad de Requisitos.', '["Seguir la vida de un requisito", "Desde el origen hasta el código"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','¿Cuál es la diferencia entre un prototipo de baja y alta fidelidad?', '["Papel vs Interactivo", "Detalle visual"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','¿Qué es una prueba UAT?', '["User Acceptance Testing", "Prueba final"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Prueba Unitaria Automatizada"},{"id":"B", "texto":"Prueba de Aceptación de Usuario"},{"id":"C", "texto":"Prueba de Carga"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','Si un requisito cambia a mitad del Desarrollo en un entorno Waterfall, ¿qué suele pasar?', '["Control de cambios", "Costoso"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Se adapta inmediatamente sin costo"},{"id":"B", "texto":"Requiere un proceso formal de control de cambios y suele ser costoso"},{"id":"C", "texto":"Se ignora el cambio"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','Describe el concepto de "Happy Path".', '["Camino ideal", "Sin errores"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 300}'::jsonb),
('PR', 'Analista TI', 'mid', 'Soporte TI','¿Qué herramienta usarías para gestionar un Backlog?', '["Jira es la más famosa", "Trello"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Photoshop"},{"id":"B", "texto":"Jira / Azure PROps"},{"id":"C", "texto":"Visual Studio Code"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','¿Cómo manejas a un Stakeholder que insiste en un requisito técnicamente inviable?', '["Negociación", "Alternativas"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 100, "max_caracteres": 1000}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','Realiza un análisis de brechas (Gap Analysis) breve para la migración de un sistema legado a la nube.', '["Estado actual vs Estado futuro", "Identificar lo que falta"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 100, "max_caracteres": 1500}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','¿Qué es la Deuda Técnica desde la perspectiva del Analista de Negocio?', '["Costo futuro", "Atajos tomados hoy"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Dinero que se debe al proveedor"},{"id":"B", "texto":"Costo implícito de retrabajo futuro por elegir una solución rápida hoy"},{"id":"C", "texto":"Falta de presupuesto"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','Describe cómo priorizar requisitos usando la técnica MoSCoW.', '["Must, Should, Could, Won''t", "Esencial vs Deseable"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','En un proyecto crítico, ¿cómo mitigas el riesgo de "Scope Creep" (Alcance no controlado)?', '["Límites claros", "Proceso de cambios estricto"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 80, "max_caracteres": 800}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','Diferencia estratégica entre BPM (Business Process Management) y BPR (Business Process Reengineering).', '["Mejora continua vs Cambio radical", "Evolución vs Revolución"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"BPM es radical, BPR es incremental"},{"id":"B", "texto":"BPM es mejora continua, BPR es rediseño radical desde cero"},{"id":"C", "texto":"Son lo mismo"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','¿Qué valor aporta un Diagrama de Secuencia en la fase de diseño técnico?', '["Interacción entre objetos", "Tiempo y mensajes"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','Ante dos departamentos con requisitos contradictorios, ¿cuál es tu estrategia de resolución?', '["Facilitador", "Objetivos de negocio superiores"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 80, "max_caracteres": 1000}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','Explica el concepto de MVP (Producto Mínimo Viable) a un cliente que quiere "todo el sistema terminado ya".', '["Valor inmediato", "Aprendizaje validado"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 800}'::jsonb),
('PR', 'Analista TI', 'sr', 'Soporte TI','¿Qué métrica utilizarías para evaluar la calidad de los requisitos definidos?', '["Tasa de defectos en requisitos", "Claridad y Completitud"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Líneas de código generadas"},{"id":"B", "texto":"Número de cambios solicitados post-aprobación (volatilidad)"},{"id":"C", "texto":"Horas de reunión"}], "respuesta_correcta":"B"}'::jsonb);

-- 2. ADMINISTRADOR DE EMPRESA (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, texto, pistas, config_respuesta) VALUES
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Qué significa las siglas FODA?', '["Análisis estratégico", "Fortalezas..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Finanzas, Organización, Dirección, Administración"},{"id":"B", "texto":"Fortalezas, Oportunidades, Debilidades, Amenazas"},{"id":"C", "texto":"Fondo de Ahorro"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Cuál es el objetivo principal de una empresa con fines de lucro?', '["Generar valor", "Rentabilidad"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Pagar impuestos"},{"id":"B", "texto":"Maximizar la riqueza de los accionistas/dueños"},{"id":"C", "texto":"Tener muchos empleados"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','Define qué es un "Activo" en contabilidad.', '["Lo que tienes", "Recursos"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Qué documento muestra la estructura jerárquica de una empresa?', '["Mapa visual de cargos", "Árbol"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Balance General"},{"id":"B", "texto":"Organigrama"},{"id":"C", "texto":"Flujograma"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Qué es la Eficacia?', '["Lograr el objetivo", "Diferente a Eficiencia"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Cuál es la función principal del departamento de Recursos Humanos?', '["Gestión de talento", "Contratación"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','En la mezcla de marketing (4P), ¿cuáles son las 4 P?', '["Producto...", "Precio..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Producto, Precio, Plaza, Promoción"},{"id":"B", "texto":"Personal, Proceso, Planta, Producción"},{"id":"C", "texto":"Planificación, Poder, Política, Prensa"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Qué significa B2B?', '["Tipo de comercio", "Business to..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Business to Business"},{"id":"B", "texto":"Business to Buyer"},{"id":"C", "texto":"Back to Basics"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','Define "Costos Fijos".', '["No varían con la producción", "Alquiler, sueldos base"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Administracion', 'jr', 'Jefe de Administración','¿Quién es la máxima autoridad formal en una Sociedad Anónima?', '["Representa a los accionistas", "Junta..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"El Gerente General"},{"id":"B", "texto":"La Junta de Accionistas"},{"id":"C", "texto":"El Contador"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','Explica qué son los objetivos SMART.', '["Específicos, Medibles...", "Acrónimo en Inglés"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Cuál es la diferencia entre Liderazgo Transaccional y Transformacional?', '["Intercambio vs Inspiración", "Premios vs Visión"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Qué mide el KPI "Rotación de Personal"?', '["Entradas y salidas", "Retención"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"La velocidad de trabajo"},{"id":"B", "texto":"El porcentaje de empleados que abandonan la organización en un periodo"},{"id":"C", "texto":"El cambio de puestos internos"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','Calcula el Punto de Equilibrio si: Costos Fijos = 1000, Precio = 50, Costo Variable = 30.', '["Fórmula: CF / (P - CV)", "Margen de contribución es 20"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"20 unidades"},{"id":"B", "texto":"50 unidades"},{"id":"C", "texto":"100 unidades"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Qué es un Diagrama de Gantt?', '["Gestión de proyectos", "Cronograma visual"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Qué estado financiero muestra la rentabilidad de la empresa en un periodo determinado?', '["Ingresos - Gastos", "Estado de Resultados"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Balance General"},{"id":"B", "texto":"Estado de Resultados (P&L)"},{"id":"C", "texto":"Flujo de Caja"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','Define la técnica de feedback "Sandwich".', '["Positivo - Mejora - Positivo", "Suavizar la crítica"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Qué es el Clima Organizacional?', '["Percepción de los empleados", "Ambiente"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','¿Cuál es la ventaja competitiva según Michael Porter?', '["Diferenciación o Costos", "Lo que te hace único"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Tener más dinero"},{"id":"B", "texto":"Una característica que permite superar a los rivales de manera sostenible"},{"id":"C", "texto":"Bajar los precios siempre"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'mid', 'Jefe de Administración','En gestión de inventarios, ¿qué es el método FIFO?', '["Lo primero que entra...", "First In First Out"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Primero en Entrar, Primero en Salir"},{"id":"B", "texto":"Último en Entrar, Primero en Salir"},{"id":"C", "texto":"Promedio Ponderado"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','Describe las 5 Fuerzas de Porter.', '["Proveedores, Clientes, Nuevos entrantes...", "Rivalidad"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 100, "max_caracteres": 1000}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','¿Cuál es la diferencia financiera entre CAPEX y OPEX?', '["Inversión vs Gasto operativo", "Largo plazo vs Día a día"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','En una fusión de empresas (M&A), ¿cuál es el mayor riesgo cultural?', '["Choque de culturas", "Resistencia al cambio"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Cambio de logo"},{"id":"B", "texto":"Pérdida de talento clave por choque cultural"},{"id":"C", "texto":"Aumento de capital"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','Explica el concepto de "Balanced Scorecard" (Cuadro de Mando Integral).', '["Kaplan y Norton", "4 perspectivas"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 80, "max_caracteres": 800}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','¿Cómo manejarías una reducción de personal del 20% para minimizar el impacto en la moral de los restantes?', '["Comunicación transparente", "Outplacement"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 100, "max_caracteres": 1500}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','¿Qué es el EBITDA y por qué es importante para valorar una empresa?', '["Earnings Before...", "Operatividad pura"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Muestra la utilidad neta final"},{"id":"B", "texto":"Muestra la capacidad de generar efectivo operativo puro, sin impuestos ni intereses"},{"id":"C", "texto":"Es el total de ventas"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','Estrategia de Océano Azul: descríbela.', '["Crear nuevos mercados", "Hacer la competencia irrelevante"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','En Responsabilidad Social Empresarial (RSE), ¿qué es el concepto de "Triple Bottom Line"?', '["Personas, Planeta, Beneficio", "3P"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Social, Ambiental, Económico"},{"id":"B", "texto":"Ventas, Costos, Utilidad"},{"id":"C", "texto":"Clientes, Proveedores, Estado"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','¿Qué harías si tu principal proveedor sube los precios un 30% repentinamente?', '["Cadena de suministro", "Diversificación"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 80, "max_caracteres": 1000}'::jsonb),
('PR', 'Administracion', 'sr', 'Jefe de Administración','Explica qué es el ROI y cómo se calcula.', '["Retorno de Inversión", "(Ganancia - Inversión) / Inversión"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb);

-- 3. INGENIERÍA INFORMÁTICA (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, texto, pistas, config_respuesta) VALUES
('PR', 'TI', 'jr','Devops Engineer', '¿Cuál es la unidad mínima de información en un computador?', '["0 o 1", "Bi..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Byte"},{"id":"B", "texto":"Bit"},{"id":"C", "texto":"Hertz"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Qué sistema numérico utilizan internamente los computadores?', '["Base 2", "Ceros y unos"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Decimal"},{"id":"B", "texto":"Hexadecimal"},{"id":"C", "texto":"Binario"}], "respuesta_correcta":"C"}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','Diferencia básica entre RAM y ROM.', '["Volátil vs No volátil", "Lectura/Escritura vs Solo lectura"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Cuál es la función principal de un Sistema Operativo?', '["Intermediario", "Gestión de recursos"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Editar textos"},{"id":"B", "texto":"Gestionar el hardware y proveer servicios a los programas"},{"id":"C", "texto":"Navegar por internet"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Qué es una dirección IP?', '["Identificador de red", "Como un número de teléfono"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Qué significan las siglas CPU?', '["Cerebro del PC", "Central..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Central Processing Unit"},{"id":"B", "texto":"Computer Personal Unit"},{"id":"C", "texto":"Central Power Unit"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','En lógica booleana, ¿cuál es el resultado de 1 AND 0?', '["Ambos deben ser 1", "Multiplicación lógica"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"1"},{"id":"B", "texto":"0"},{"id":"C", "texto":"Null"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Qué es el Hardware?', '["Parte física", "Lo que puedes tocar"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 10, "max_caracteres": 150}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Para qué sirve un algoritmo?', '["Secuencia de pasos", "Resolver problemas"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'TI', 'jr', 'Devops Engineer','¿Cuál es el componente encargado de los gráficos en un PC?', '["GPU", "Tarjeta..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"CPU"},{"id":"B", "texto":"GPU"},{"id":"C", "texto":"SSD"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','Explica qué es la virtualización.', '["Máquinas virtuales", "Abstraer hardware"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿En qué capa del modelo OSI funciona el protocolo IP?', '["Red", "Capa 3"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Capa 2 (Enlace)"},{"id":"B", "texto":"Capa 3 (Red)"},{"id":"C", "texto":"Capa 4 (Transporte)"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿Qué es RAID 1 y para qué sirve?', '["Espejo", "Redundancia"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','Diferencia entre TCP y UDP.', '["Fiabilidad vs Velocidad", "Conexión vs Sin conexión"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"TCP es más rápido, UDP es seguro"},{"id":"B", "texto":"TCP garantiza entrega (orientado a conexión), UDP no (streaming)"},{"id":"C", "texto":"Son iguales"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿Qué es la Normalización en Bases de Datos?', '["Evitar redundancia", "Formas normales"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿Qué función cumple un servidor DNS?', '["Traduce nombres a IP", "Directorio telefónico de internet"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Asigna IPs dinámicas"},{"id":"B", "texto":"Traduce nombres de dominio a direcciones IP"},{"id":"C", "texto":"Encripta la conexión"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','Describe el concepto de "Cloud Computing".', '["Servicios a través de internet", "Bajo demanda"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿Qué es un Firewall?', '["Cortafuegos", "Seguridad de red"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un antivirus"},{"id":"B", "texto":"Sistema que controla el tráfico de red entrante y saliente"},{"id":"C", "texto":"Un cable de red blindado"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','¿Qué es el Kernel de un Sistema Operativo?', '["Núcleo", "Control directo del hardware"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'TI', 'mid', 'Devops Engineer','En criptografía asimétrica, ¿qué clave se comparte públicamente?', '["Pública vs Privada", "Para encriptar o verificar"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Clave Privada"},{"id":"B", "texto":"Clave Pública"},{"id":"C", "texto":"Ninguna"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','Diseña una arquitectura de Alta Disponibilidad (HA) básica para una web crítica.', '["Balanceadores", "Redundancia", "Multi-AZ"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 80, "max_caracteres": 1000}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','Explica el funcionamiento de un ataque DDoS y cómo mitigarlo.', '["Denegación distribuida", "CDN, WAF"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 60, "max_caracteres": 800}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','¿Qué es un Container Orchestrator (ej: Kubernetes) y por qué es necesario en grandes sistemas?', '["Gestión de ciclo de vida", "Escalado automático"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Es un antivirus para contenedores"},{"id":"B", "texto":"Automatiza el despliegue, escalado y gestión de aplicaciones en contenedores"},{"id":"C", "texto":"Es un lenguaje de programación"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','Diferencia entre Escalado Vertical y Horizontal.', '["Más potencia vs Más máquinas", "CPU vs Nodos"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Vertical es agregar más máquinas, Horizontal es mejorar la máquina"},{"id":"B", "texto":"Vertical es mejorar la máquina (más RAM/CPU), Horizontal es agregar más máquinas"},{"id":"C", "texto":"Son lo mismo"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','¿Qué es "Infrastructure as Code" (IaC)?', '["Terraform, Ansible", "Infraestructura programable"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','En el contexto de Big Data, explica las 3 V.', '["Volumen, Velocidad, Variedad", "Datos masivos"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','¿Qué es un plan de DRP (Disaster Recovery Plan)?', '["Recuperación ante desastres", "Continuidad de negocio"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','Explica el concepto de "Zero Trust Security".', '["No confiar en nadie", "Verificar siempre"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Confiar solo en la red interna"},{"id":"B", "texto":"Modelo donde no se confía en ningún usuario o dispositivo, dentro o fuera del perímetro"},{"id":"C", "texto":"No usar contraseñas"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','¿Qué es Latencia y cómo afecta a los sistemas distribuidos?', '["Retardo", "Tiempo de viaje del paquete"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'TI', 'sr', 'Devops Engineer','¿Cuál es la principal ventaja de usar una arquitectura "Serverless"?', '["No gestionas servidores", "Pago por uso"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Mayor control del hardware"},{"id":"B", "texto":"Abstracción total del servidor y modelo de costos por ejecución"},{"id":"C", "texto":"Es gratis"}], "respuesta_correcta":"B"}'::jsonb);

-- 4. DESARROLLADOR (Código: PR)
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, texto, pistas, config_respuesta) VALUES
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Qué imprime "console.log(typeof [])" en JavaScript?', '["Arrays son objetos", "Curiosidad de JS"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"array"},{"id":"B", "texto":"object"},{"id":"C", "texto":"list"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Para qué sirve el operador "++" en muchos lenguajes?', '["Incremento", "Sumar uno"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Suma dos variables"},{"id":"B", "texto":"Incrementa el valor de la variable en 1"},{"id":"C", "texto":"Concatena strings"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Qué es un bucle "infinito"?', '["Nunca termina", "Condición siempre true"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','En Git, ¿qué comando descarga los cambios del remoto al local?', '["Traer cambios", "Pull..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"git push"},{"id":"B", "texto":"git pull"},{"id":"C", "texto":"git commit"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Qué es una variable?', '["Espacio de memoria", "Contenedor"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','En CSS, ¿qué propiedad cambia el color de fondo?', '["Background...", "Color es para texto"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"color"},{"id":"B", "texto":"background-color"},{"id":"C", "texto":"border"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Qué es el DOM en Desarrollor web?', '["Document Object Model", "Árbol de elementos"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Cuál es el índice del primer elemento en un array (en la mayoría de lenguajes)?', '["Empieza en...", "Cero"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"0"},{"id":"B", "texto":"1"},{"id":"C", "texto":"-1"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','¿Qué significa IDE?', '["Entorno de Desarrollor", "Integrated..."]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Integrated PRelopment Environment"},{"id":"B", "texto":"Internet PRelopment Explorer"},{"id":"C", "texto":"Internal Data Exchange"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack','Escribe una función simple que sume dos números (pseudocódigo).', '["function suma(a,b)...", "return..."]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Qué es la Inyección de Dependencias?', '["Patrón de diseño", "Inversión de control"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','En una API REST, ¿qué verbo HTTP se usa para actualizar parcialmente un recurso?', '["No es PUT", "Parcial"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"PUT"},{"id":"B", "texto":"PATCH"},{"id":"C", "texto":"POST"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','Explica el concepto de "Callback" en programación asíncrona.', '["Función pasada como argumento", "Se ejecuta después"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Qué diferencia hay entre "git merge" y "git rebase"?', '["Historial lineal vs Historial ramificado", "Reescritura"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Merge reescribe la historia, Rebase crea un commit de unión"},{"id":"B", "texto":"Rebase reescribe la historia linealmente, Merge crea un commit de unión"},{"id":"C", "texto":"Son idénticos"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Qué es un ORM?', '["Object Relational Mapping", "Base de datos como objetos"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','En POO, ¿qué es el Polimorfismo?', '["Muchas formas", "Mismo método, diferente comportamiento"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"La capacidad de heredar atributos"},{"id":"B", "texto":"Capacidad de objetos de diferentes clases de responder al mismo mensaje de distinta manera"},{"id":"C", "texto":"Ocultar datos privados"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Qué es el "Scope" (alcance) de una variable?', '["Dónde vive la variable", "Global vs Local"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Por qué usarías Docker en Desarrollor?', '["Entornos consistentes", "Funciona en mi máquina"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Para hacer el código más rápido"},{"id":"B", "texto":"Para garantizar paridad entre entornos de Desarrollor y producción"},{"id":"C", "texto":"Para diseñar interfaces"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','¿Qué es MVC?', '["Modelo Vista Controlador", "Patrón de arquitectura"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200}'::jsonb),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack','Identifica el error: "SELECT * FROM users WHERE name = ''Pepe"', '["Faltan comillas", "Sintaxis SQL"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Falta cerrar la comilla simple"},{"id":"B", "texto":"Falta el punto y coma"},{"id":"C", "texto":"Users va con mayúscula"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','Explica qué es una "Race Condition" (Condición de Carrera).', '["Concurrencia", "Resultados impredecibles"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','En Arquitectura de Software, ¿qué es el patrón Singleton y cuándo es peligroso?', '["Instancia única", "Estado global mutable"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','¿Qué principio SOLID se viola si una clase tiene demasiadas responsabilidades?', '["Single Responsibility", "La S de SOLID"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"SRP (Single Responsibility Principle)"},{"id":"B", "texto":"OCP (Open/Closed Principle)"},{"id":"C", "texto":"LSP (Liskov Substitution Principle)"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','¿Qué es un "Memory Leak" y cómo lo detectas?', '["Fuga de memoria", "El consumo de RAM crece sin parar"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','Comparación: Monolito vs Microservicios. ¿Cuándo NO usarías microservicios?', '["Complejidad", "Equipos pequeños"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 60, "max_caracteres": 800}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','En bases de datos, ¿qué es una transacción ACID?', '["Atomicidad, Consistencia...", "Todo o nada"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un tipo de base de datos NoSQL"},{"id":"B", "texto":"Un conjunto de propiedades que garantizan la validez de las transacciones"},{"id":"C", "texto":"Un virus informático"}], "respuesta_correcta":"B"}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','¿Qué es la complejidad ciclomática?', '["Métrica de código", "Caminos independientes"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','Estrategias de Caché: Diferencia entre Cache-Aside y Write-Through.', '["Lectura vs Escritura", "Quién carga los datos"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Cache-Aside la app carga los datos si no están; Write-Through escribe en caché y DB a la vez"},{"id":"B", "texto":"Son lo mismo"},{"id":"C", "texto":"Write-Through es solo para lectura"}], "respuesta_correcta":"A"}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','¿Qué es la Idempotencia en una API REST?', '["Repetir la llamada", "Mismo resultado"]'::jsonb, '{"tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400}'::jsonb),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack','¿Qué es el teorema CAP?', '["Distribuido", "Escoge 2 de 3"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Consistency, Availability, Partition Tolerance"},{"id":"B", "texto":"Capacity, Availability, Performance"},{"id":"C", "texto":"Code, App, Program"}], "respuesta_correcta":"A"}'::jsonb);

COMMIT;

-- =============================================================================
-- 4. CONSENTIMIENTO INICIAL
-- =============================================================================
BEGIN;
INSERT INTO consentimiento_texto (version, titulo, cuerpo)
VALUES ('v1.0','Consentimiento de uso de datos','Texto completo del consentimiento que verán los usuarios.');
COMMIT;
-- =============================================================================
-- 3. CREACIÓN DE USUARIOS ADMIN
-- =============================================================================

-- Insertamos los administradores
BEGIN;
INSERT INTO usuario (correo, contrasena_hash, nombre, idioma, estado, rol) VALUES
(
    'admin@entrevista.com',
    '$argon2id$v=19$m=19456,t=2,p=1$ohYeqdkuF1wBlmYhTi5uow$p3mUFWphjPNNU4fVkbFL7IICdDJnB8bDlbFXoycJjOA',
    'Admin inicial',
    'es',
    'activo',
    'admin'
);
COMMIT;