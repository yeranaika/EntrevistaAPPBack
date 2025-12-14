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
    pregunta_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco         VARCHAR(5),      -- 'NV' (nivelación), 'PR' (práctica), etc.
    sector             VARCHAR(80),     -- área: 'TI', 'Administracion', etc.
    nivel              VARCHAR(3),      -- 'jr', 'ssr', 'sr', o '1','2','3' en NV
    meta_cargo         VARCHAR(120),    -- cargo objetivo (opcional)
    tipo_pregunta      VARCHAR(20) NOT NULL DEFAULT 'opcion_multiple'
                       CHECK (tipo_pregunta IN ('opcion_multiple','abierta')),
    texto              TEXT NOT NULL,   -- enunciado
    pistas             JSONB,           -- hints / tags / explicaciones extra
    config_respuesta   JSONB,           -- opciones y/o criterios de corrección
    config_evaluacion  JSONB,           -- NLP + STAR + parámetros para LLM
    activa             BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion     TIMESTAMPTZ NOT NULL DEFAULT now()
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
    feedback_general_v2   JSONB,
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
INSERT INTO pregunta (
    tipo_banco, sector, nivel, meta_cargo,
    tipo_pregunta, texto, pistas,
    config_respuesta, config_evaluacion
) VALUES
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un Requisito Funcional?',
 '["Describe lo que el sistema debe hacer", "Comportamiento"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Cómo se ve el sistema"},{"id":"B","texto":"Una función o servicio que el sistema debe proveer"},{"id":"C","texto":"La velocidad del sistema"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un requisito funcional describe una función o servicio que el sistema debe proveer.","explicacion_incorrecta":"No se centra en apariencia ni rendimiento, sino en el comportamiento esperado del sistema."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 'En un diagrama de flujo, ¿qué forma representa una decisión?',
 '["Tiene forma de diamante", "Suelen salir flechas de SI/NO"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Rectángulo"},{"id":"B","texto":"Rombo/Diamante"},{"id":"C","texto":"Círculo"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Las decisiones se representan con un rombo o diamante, del que salen ramas de Sí/No u opciones.","explicacion_incorrecta":"Los rectángulos representan procesos y los círculos suelen usarse como inicio/fin."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué significan las siglas UML?',
 '["Lenguaje visual estándar", "Unified..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Universal Modeling List"},{"id":"B","texto":"Unified Modeling Language"},{"id":"C","texto":"User Management Logic"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"UML significa Unified Modeling Language, un lenguaje visual estándar para modelar sistemas.","explicacion_incorrecta":"No está relacionado con listas ni con gestión de usuarios."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Define brevemente qué es un "Stakeholder".',
 '["Interesado", "Puede afectar o ser afectado"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["persona o grupo interesado","afectado por el proyecto","influye en el proyecto"],"palabras_penalizadas":["no se","nose","ni idea"]},"feedback_generico":"Se espera que definas stakeholder como cualquier persona o grupo que puede afectar o ser afectado por el proyecto o sistema."}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Cuál es el actor principal en un Caso de Uso de "Login"?',
 '["Quien inicia la acción", "Persona frente al PC"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Usuario"},{"id":"B","texto":"Base de Datos"},{"id":"C","texto":"Servidor"}],"respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El actor principal es el usuario que inicia la acción de login.","explicacion_incorrecta":"La base de datos y el servidor son componentes internos del sistema, no actores externos."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Diferencia principal entre Requisito Funcional y No Funcional.',
 '["El Qué vs el Cómo", "Calidad vs Comportamiento"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["requisito funcional describe qué hace el sistema","requisito no funcional describe cómo lo hace","calidad o restricciones","rendimiento","seguridad"]},"feedback_generico":"Se espera que expliques que los requisitos funcionales describen el qué hace el sistema y los no funcionales el cómo, calidad o restricciones bajo las que funciona."}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 'En metodología Ágil, ¿quién suele priorizar el Backlog?',
 '["Representa al negocio", "Product..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Scrum Master"},{"id":"B","texto":"Product Owner"},{"id":"C","texto":"El Desarrollador"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"En Scrum el Product Owner prioriza el Product Backlog según el valor para el negocio.","explicacion_incorrecta":"El Scrum Master facilita y el equipo desarrolla, pero no son responsables directos de priorizar."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 '¿Qué es un "Bug"?',
 '["Error", "Fallo en el software"]'::jsonb,
 '{"min_caracteres":10,"max_caracteres":150}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["error en el software","comportamiento inesperado","falla en la aplicación"]},"feedback_generico":"Se espera que menciones que un bug es un error o fallo en el software que provoca un comportamiento incorrecto o inesperado."}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Para qué sirve una entrevista de levantamiento de información?',
 '["Técnica de educción", "Hablar con el cliente"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Para programar el código"},{"id":"B","texto":"Para entender las necesidades del usuario"},{"id":"C","texto":"Para vender el producto"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La entrevista de levantamiento sirve para entender necesidades, requisitos y contexto del usuario o cliente.","explicacion_incorrecta":"No es una actividad de programación ni de venta directa."}}'::jsonb
),
('PR', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
 'Menciona 3 técnicas para recopilar requisitos.',
 '["Entrevistas...", "Encuestas..."]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["entrevistas","encuestas","talleres","observación","prototipos","análisis de documentos"]},"feedback_generico":"Se esperan al menos tres técnicas típicas de levantamiento, por ejemplo entrevistas, encuestas, talleres, prototipos u observación."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Escribe el formato estándar de una Historia de Usuario.',
 '["Como [rol]...", "Quiero [acción]..."]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["como","quiero","para"],"patron_ejemplo":"Como <rol> quiero <función> para <beneficio>"},"feedback_generico":"Se espera el patrón típico: Como <rol> quiero <función> para <beneficio>."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué diagrama UML usarías para mostrar los estados por los que pasa una orden de compra?',
 '["Inicio, Pendiente, Aprobado, Fin", "Máquina de..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Diagrama de Clases"},{"id":"B","texto":"Diagrama de Estados"},{"id":"C","texto":"Diagrama de Despliegue"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Para mostrar los estados por los que pasa una orden se utiliza un diagrama de estados.","explicacion_incorrecta":"Los diagramas de clases modelan estructuras y los de despliegue la infraestructura física."}}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 '¿Qué es el criterio de aceptación?',
 '["Condiciones para dar por terminada una tarea", "Definition of Done"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["condiciones que debe cumplir","para considerar una historia completada","validación del usuario","define cuándo algo está aceptado"]},"feedback_generico":"Se espera que expliques que son condiciones claras que deben cumplirse para que el trabajo sea aceptado por el usuario o negocio."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 'En BPMN, ¿qué representa un carril (Swimlane)?',
 '["Responsabilidad", "Actor o departamento"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Una decisión lógica"},{"id":"B","texto":"Un actor o rol responsable de las tareas"},{"id":"C","texto":"El flujo de datos"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un carril representa a un actor, rol o área responsable de un conjunto de tareas.","explicacion_incorrecta":"No representa decisiones ni flujos de datos por sí mismo."}}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Explica qué es la Trazabilidad de Requisitos.',
 '["Seguir la vida de un requisito", "Desde el origen hasta el código"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["seguir el requisito a lo largo de su ciclo de vida","desde su origen hasta pruebas o código","relación entre requisitos, diseño y pruebas"]},"feedback_generico":"Se espera que menciones que la trazabilidad permite seguir un requisito desde su origen hasta el diseño, desarrollo y pruebas."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 '¿Cuál es la diferencia entre un prototipo de baja y alta fidelidad?',
 '["Papel vs Interactivo", "Detalle visual"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["baja fidelidad es simple o en papel","poco detalle visual","alta fidelidad se parece al producto final","interactivo","más detalle visual"]},"feedback_generico":"Se espera que expliques que la baja fidelidad es simple, suele hacerse en papel o boceto, y la alta fidelidad es más detallada e interactiva, cercana al producto final."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué es una prueba UAT?',
 '["User Acceptance Testing", "Prueba final"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Prueba Unitaria Automatizada"},{"id":"B","texto":"Prueba de Aceptación de Usuario"},{"id":"C","texto":"Prueba de Carga"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"UAT es User Acceptance Testing, pruebas realizadas por usuarios o negocio para aceptar la solución.","explicacion_incorrecta":"No es una prueba unitaria ni de carga, sino de aceptación."}}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 'Si un requisito cambia a mitad del Desarrollo en un entorno Waterfall, ¿qué suele pasar?',
 '["Control de cambios", "Costoso"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Se adapta inmediatamente sin costo"},{"id":"B","texto":"Requiere un proceso formal de control de cambios y suele ser costoso"},{"id":"C","texto":"Se ignora el cambio"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"En modelos Waterfall los cambios se gestionan mediante un proceso formal de control de cambios y suelen tener impacto en coste y plazos.","explicacion_incorrecta":"No se adaptan de forma inmediata y gratuita, ni se deberían ignorar."}}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'abierta',
 'Describe el concepto de "Happy Path".',
 '["Camino ideal", "Sin errores"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["camino ideal","sin errores ni excepciones","flujo principal","escenario donde todo sale bien"]},"feedback_generico":"Se espera que definas el Happy Path como el flujo ideal donde todo sale bien, sin errores ni excepciones."}'::jsonb
),
('PR', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
 '¿Qué herramienta usarías para gestionar un Backlog?',
 '["Jira es la más famosa", "Trello"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Photoshop"},{"id":"B","texto":"Jira / Azure PROps"},{"id":"C","texto":"Visual Studio Code"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Herramientas como Jira o Azure Boards se utilizan habitualmente para gestionar el backlog de producto.","explicacion_incorrecta":"Photoshop y los IDEs no son herramientas de gestión de backlog."}}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 '¿Cómo manejas a un Stakeholder que insiste en un requisito técnicamente inviable?',
 '["Negociación", "Alternativas"]'::jsonb,
 '{"min_caracteres":100,"max_caracteres":1000}'::jsonb,
 '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["explicar limitaciones técnicas o de costo","proponer alternativas viables","negociación basada en valor de negocio","gestión de expectativas"]},"feedback_generico":"Se espera que describas cómo explicas las limitaciones, propones alternativas viables y negocias priorizando el valor de negocio."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Realiza un análisis de brechas (Gap Analysis) breve para la migración de un sistema legado a la nube.',
 '["Estado actual vs Estado futuro", "Identificar lo que falta"]'::jsonb,
 '{"min_caracteres":100,"max_caracteres":1500}'::jsonb,
 '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["estado actual on-premise","estado futuro en la nube","brechas o diferencias","plan de acciones para cerrar brechas"]},"feedback_generico":"Se espera que menciones el estado actual, el estado objetivo en la nube, las brechas identificadas y acciones para cerrarlas."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es la Deuda Técnica desde la perspectiva del Analista de Negocio?',
 '["Costo futuro", "Atajos tomados hoy"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Dinero que se debe al proveedor"},{"id":"B","texto":"Costo implícito de retrabajo futuro por elegir una solución rápida hoy"},{"id":"C","texto":"Falta de presupuesto"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La deuda técnica es el costo futuro de retrabajo por decisiones rápidas o soluciones subóptimas tomadas hoy.","explicacion_incorrecta":"No es una deuda financiera directa ni simplemente falta de presupuesto."}}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Describe cómo priorizar requisitos usando la técnica MoSCoW.',
 '["Must, Should, Could, Won''t", "Esencial vs Deseable"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":600}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["clasificar en Must Have","Should Have","Could Have","Won''t Have","priorización según valor y necesidad"]},"feedback_generico":"Se espera que expliques las categorías Must, Should, Could y Won''t Have y cómo se usan para priorizar requisitos según valor y necesidad."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'En un proyecto crítico, ¿cómo mitigas el riesgo de "Scope Creep" (Alcance no controlado)?',
 '["Límites claros", "Proceso de cambios estricto"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["definir claramente el alcance","control formal de cambios","gestión de expectativas","priorización con negocio"]},"feedback_generico":"Se espera que hables de definir bien el alcance, usar un proceso formal de control de cambios y gestionar expectativas con los stakeholders."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 'Diferencia estratégica entre BPM (Business Process Management) y BPR (Business Process Reengineering).',
 '["Mejora continua vs Cambio radical", "Evolución vs Revolución"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"BPM es radical, BPR es incremental"},{"id":"B","texto":"BPM es mejora continua, BPR es rediseño radical desde cero"},{"id":"C","texto":"Son lo mismo"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"BPM se centra en la mejora continua de procesos, mientras que BPR implica un rediseño radical desde cero.","explicacion_incorrecta":"No son lo mismo ni se invierten los conceptos incremental y radical."}}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 '¿Qué valor aporta un Diagrama de Secuencia en la fase de diseño técnico?',
 '["Interacción entre objetos", "Tiempo y mensajes"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["muestra interacción entre componentes u objetos","orden temporal de los mensajes","ayuda a entender el flujo de llamadas"]},"feedback_generico":"Se espera que expliques que muestra cómo interactúan los componentes en el tiempo, qué mensajes se envían y en qué orden."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Ante dos departamentos con requisitos contradictorios, ¿cuál es tu estrategia de resolución?',
 '["Facilitador", "Objetivos de negocio superiores"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":1000}'::jsonb,
 '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["facilitar una sesión de alineación","entender intereses de cada parte","negociar en función de objetivos de negocio","buscar compromiso o solución intermedia"]},"feedback_generico":"Se espera que describas cómo facilitas el diálogo, clarificas intereses, te apoyas en los objetivos de negocio y buscas una solución acordada."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
 'Explica el concepto de MVP (Producto Mínimo Viable) a un cliente que quiere "todo el sistema terminado ya".',
 '["Valor inmediato", "Aprendizaje validado"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":800}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["versión mínima que aporta valor","validar hipótesis","aprendizaje con usuarios reales","entregar algo usable rápido"]},"feedback_generico":"Se espera que expliques que el MVP es la versión mínima del producto que aporta valor y permite aprender rápido con usuarios reales antes de construir todo."}'::jsonb
),
('PR', 'Analista TI', 'sr', 'Soporte TI', 'opcion_multiple',
 '¿Qué métrica utilizarías para evaluar la calidad de los requisitos definidos?',
 '["Tasa de defectos en requisitos", "Claridad y Completitud"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Líneas de código generadas"},{"id":"B","texto":"Número de cambios solicitados post-aprobación (volatilidad)"},{"id":"C","texto":"Horas de reunión"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La volatilidad de requisitos (cambios post-aprobación) es un buen indicador de la calidad y estabilidad de los requisitos.","explicacion_incorrecta":"Las líneas de código o las horas de reunión no miden directamente la calidad de los requisitos."}}'::jsonb
);

-- 2. ADMINISTRADOR DE EMPRESA (Código: PR)
INSERT INTO pregunta (
    tipo_banco, sector, nivel, meta_cargo,
    tipo_pregunta, texto, pistas,
    config_respuesta, config_evaluacion
) VALUES
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué significa las siglas FODA?',
 '["Análisis estratégico", "Fortalezas..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Finanzas, Organización, Dirección, Administración"},{"id":"B","texto":"Fortalezas, Oportunidades, Debilidades, Amenazas"},{"id":"C","texto":"Fondo de Ahorro"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"FODA significa Fortalezas, Oportunidades, Debilidades y Amenazas, un análisis estratégico clásico.","explicacion_incorrecta":"No se refiere a finanzas ni a fondos de ahorro."}}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Cuál es el objetivo principal de una empresa con fines de lucro?',
 '["Generar valor", "Rentabilidad"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Pagar impuestos"},{"id":"B","texto":"Maximizar la riqueza de los accionistas/dueños"},{"id":"C","texto":"Tener muchos empleados"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El objetivo principal es maximizar el valor o riqueza de los dueños o accionistas.","explicacion_incorrecta":"Pagar impuestos es una obligación, no el objetivo central."}}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 'Define qué es un "Activo" en contabilidad.',
 '["Lo que tienes", "Recursos"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["recurso controlado","generar beneficios futuros","propiedad de la empresa"]},"feedback_generico":"Se espera que menciones que un activo es un recurso controlado por la empresa del que se esperan beneficios económicos futuros."}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué documento muestra la estructura jerárquica de una empresa?',
 '["Mapa visual de cargos", "Árbol"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Balance General"},{"id":"B","texto":"Organigrama"},{"id":"C","texto":"Flujograma"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El organigrama muestra gráficamente la estructura jerárquica de la organización.","explicacion_incorrecta":"El balance y el flujograma cumplen otras funciones financieras o de procesos."}}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 '¿Qué es la Eficacia?',
 '["Lograr el objetivo", "Diferente a Eficiencia"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["grado de cumplimiento de objetivos","lograr resultados esperados","distinto de eficiencia"]},"feedback_generico":"Se espera que definas eficacia como el grado en que se logran los objetivos propuestos, diferenciándola de la eficiencia."}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 '¿Cuál es la función principal del departamento de Recursos Humanos?',
 '["Gestión de talento", "Contratación"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["gestión del talento","reclutamiento y selección","desarrollo y capacitación","clima laboral"]},"feedback_generico":"Se espera que menciones que RRHH gestiona el talento: atraer, desarrollar y retener a las personas."}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 'En la mezcla de marketing (4P), ¿cuáles son las 4 P?',
 '["Producto...", "Precio..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Producto, Precio, Plaza, Promoción"},{"id":"B","texto":"Personal, Proceso, Planta, Producción"},{"id":"C","texto":"Planificación, Poder, Política, Prensa"}],"respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Las 4P tradicionales son Producto, Precio, Plaza y Promoción.","explicacion_incorrecta":"Las otras opciones mezclan conceptos que no corresponden al modelo clásico."}}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué significa B2B?',
 '["Tipo de comercio", "Business to..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Business to Business"},{"id":"B","texto":"Business to Buyer"},{"id":"C","texto":"Back to Basics"}],"respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"B2B significa Business to Business, comercio entre empresas.","explicacion_incorrecta":"No significa Business to Buyer ni Back to Basics."}}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'abierta',
 'Define "Costos Fijos".',
 '["No varían con la producción", "Alquiler, sueldos base"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["no cambian con el nivel de producción","alquiler","sueldos fijos","seguros"]},"feedback_generico":"Se espera que indiques que son costos que no varían con el volumen producido en el corto plazo, como arriendos o sueldos fijos."}'::jsonb
),
('PR', 'Administracion', 'jr', 'Jefe de Administración', 'opcion_multiple',
 '¿Quién es la máxima autoridad formal en una Sociedad Anónima?',
 '["Representa a los accionistas", "Junta..."]'::jsonb,
 '{"opciones":[{"id":"A","texto":"El Gerente General"},{"id":"B","texto":"La Junta de Accionistas"},{"id":"C","texto":"El Contador"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La junta de accionistas es la máxima autoridad formal en una sociedad anónima.","explicacion_incorrecta":"El gerente general ejecuta, pero no es la máxima instancia de gobierno."}}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 'Explica qué son los objetivos SMART.',
 '["Específicos, Medibles...", "Acrónimo en Inglés"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["específicos","medibles","alcanzables","relevantes","acotados en el tiempo"]},"feedback_generico":"Se espera que menciones que SMART significa objetivos específicos, medibles, alcanzables, relevantes y con plazo definido."}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Cuál es la diferencia entre Liderazgo Transaccional y Transformacional?',
 '["Intercambio vs Inspiración", "Premios vs Visión"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["transaccional se basa en intercambio de recompensas","transformacional inspira y motiva","visión de cambio","más allá de recompensas económicas"]},"feedback_generico":"Se espera que expliques que el liderazgo transaccional se basa en intercambio de recompensas por desempeño, y el transformacional en inspirar y cambiar la visión."}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué mide el KPI "Rotación de Personal"?',
 '["Entradas y salidas", "Retención"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"La velocidad de trabajo"},{"id":"B","texto":"El porcentaje de empleados que abandonan la organización en un periodo"},{"id":"C","texto":"El cambio de puestos internos"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La rotación de personal mide el porcentaje de empleados que salen de la organización en un periodo.","explicacion_incorrecta":"No mide velocidad de trabajo ni simples cambios de puesto internos."}}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 'Calcula el Punto de Equilibrio si: Costos Fijos = 1000, Precio = 50, Costo Variable = 30.',
 '["Fórmula: CF / (P - CV)", "Margen de contribución es 20"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"20 unidades"},{"id":"B","texto":"50 unidades"},{"id":"C","texto":"100 unidades"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El punto de equilibrio se calcula como 1000 dividido en 20, dando 50 unidades.","explicacion_incorrecta":"Las otras alternativas no aplican correctamente la fórmula de punto de equilibrio."}}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Qué es un Diagrama de Gantt?',
 '["Gestión de proyectos", "Cronograma visual"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["cronograma de proyecto","barras de tiempo","tareas y duración","seguimiento de avance"]},"feedback_generico":"Se espera que lo describas como un cronograma visual de proyecto en forma de barras de tiempo."}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué estado financiero muestra la rentabilidad de la empresa en un periodo determinado?',
 '["Ingresos - Gastos", "Estado de Resultados"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Balance General"},{"id":"B","texto":"Estado de Resultados (P&L)"},{"id":"C","texto":"Flujo de Caja"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La rentabilidad del periodo se ve en el estado de resultados, que muestra ingresos y gastos.","explicacion_incorrecta":"El balance muestra situación a una fecha y el flujo de caja movimientos de efectivo."}}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 'Define la técnica de feedback "Sandwich".',
 '["Positivo - Mejora - Positivo", "Suavizar la crítica"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["comentario positivo inicial","crítica o área de mejora en el centro","comentario positivo final"]},"feedback_generico":"Se espera que expliques que consiste en dar un mensaje positivo, luego la mejora, y cerrar nuevamente con algo positivo."}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'abierta',
 '¿Qué es el Clima Organizacional?',
 '["Percepción de los empleados", "Ambiente"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["percepción de los empleados","ambiente laboral","relaciones internas","satisfacción en el trabajo"]},"feedback_generico":"Se espera que lo definas como la percepción que tienen los empleados sobre el ambiente y las relaciones dentro de la organización."}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Cuál es la ventaja competitiva según Michael Porter?',
 '["Diferenciación o Costos", "Lo que te hace único"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Tener más dinero"},{"id":"B","texto":"Una característica que permite superar a los rivales de manera sostenible"},{"id":"C","texto":"Bajar los precios siempre"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La ventaja competitiva es aquello que permite superar a los rivales de forma sostenible, ya sea por costos o diferenciación.","explicacion_incorrecta":"No es solo tener más dinero ni bajar precios sin estrategia."}}'::jsonb
),
('PR', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 'En gestión de inventarios, ¿qué es el método FIFO?',
 '["Lo primero que entra...", "First In First Out"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Primero en Entrar, Primero en Salir"},{"id":"B","texto":"Último en Entrar, Primero en Salir"},{"id":"C","texto":"Promedio Ponderado"}],"respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"FIFO es Primero en Entrar, Primero en Salir, se venden primero las unidades más antiguas.","explicacion_incorrecta":"Las otras opciones corresponden a otros métodos de valoración o son incorrectas."}}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Describe las 5 Fuerzas de Porter.',
 '["Proveedores, Clientes, Nuevos entrantes...", "Rivalidad"]'::jsonb,
 '{"min_caracteres":100,"max_caracteres":1000}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["poder de negociación de proveedores","poder de negociación de clientes","amenaza de nuevos entrantes","amenaza de productos sustitutos","rivalidad entre competidores"]},"feedback_generico":"Se espera que enumeres y expliques brevemente las cinco fuerzas: proveedores, clientes, nuevos entrantes, sustitutos y rivalidad existente."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Cuál es la diferencia financiera entre CAPEX y OPEX?',
 '["Inversión vs Gasto operativo", "Largo plazo vs Día a día"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":600}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["capex es gasto de inversión","activos de largo plazo","opex es gasto operativo","costos del día a día"]},"feedback_generico":"Se espera que expliques que CAPEX son inversiones en activos de largo plazo y OPEX son gastos operativos recurrentes."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 'En una fusión de empresas (M&A), ¿cuál es el mayor riesgo cultural?',
 '["Choque de culturas", "Resistencia al cambio"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Cambio de logo"},{"id":"B","texto":"Pérdida de talento clave por choque cultural"},{"id":"C","texto":"Aumento de capital"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Uno de los mayores riesgos es la pérdida de talento clave por choque cultural y mala integración.","explicacion_incorrecta":"Cambiar el logo o aumentar capital no son los principales riesgos culturales."}}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica el concepto de "Balanced Scorecard" (Cuadro de Mando Integral).',
 '["Kaplan y Norton", "4 perspectivas"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":800}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["herramienta de gestión estratégica","perspectivas financiera","del cliente","de procesos internos","de aprendizaje y crecimiento"]},"feedback_generico":"Se espera que lo describas como un marco de gestión estratégica que equilibra indicadores financieros y no financieros en varias perspectivas."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Cómo manejarías una reducción de personal del 20% para minimizar el impacto en la moral de los restantes?',
 '["Comunicación transparente", "Outplacement"]'::jsonb,
 '{"min_caracteres":100,"max_caracteres":1500}'::jsonb,
 '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["comunicación transparente y oportuna","apoyo a las personas afectadas","respetar procesos legales","cuidar la moral y carga de trabajo de quienes se quedan"]},"feedback_generico":"Se espera que describes medidas de comunicación, apoyo, planificación y cuidado del equipo que permanece, idealmente con un enfoque estructurado."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el EBITDA y por qué es importante para valorar una empresa?',
 '["Earnings Before...", "Operatividad pura"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Muestra la utilidad neta final"},{"id":"B","texto":"Muestra la capacidad de generar efectivo operativo puro, sin impuestos ni intereses"},{"id":"C","texto":"Es el total de ventas"}],"respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El EBITDA mide el resultado operativo antes de intereses, impuestos, depreciaciones y amortizaciones, útil para comparar desempeño operativo.","explicacion_incorrecta":"No es la utilidad neta ni simplemente las ventas."}}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Estrategia de Océano Azul: descríbela.',
 '["Crear nuevos mercados", "Hacer la competencia irrelevante"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":600}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["crear nuevos espacios de mercado","competencia irrelevante","innovación en valor"]},"feedback_generico":"Se espera que menciones que la estrategia de océano azul busca crear nuevos espacios de mercado donde la competencia sea irrelevante, mediante innovación en valor."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 'En Responsabilidad Social Empresarial (RSE), ¿qué es el concepto de "Triple Bottom Line"?',
 '["Personas, Planeta, Beneficio", "3P"]'::jsonb,
 '{"opciones":[{"id":"A","texto":"Social, Ambiental, Económico"},{"id":"B","texto":"Ventas, Costos, Utilidad"},{"id":"C","texto":"Clientes, Proveedores, Estado"}],"respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El triple bottom line integra desempeño social, ambiental y económico.","explicacion_incorrecta":"No se limita a variables puramente financieras o de relación comercial."}}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Qué harías si tu principal proveedor sube los precios un 30% repentinamente?',
 '["Cadena de suministro", "Diversificación"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":1000}'::jsonb,
 '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["análisis de impacto en costos","buscar proveedores alternativos","negociar condiciones","revisar precios y contratos","gestión de riesgo en la cadena de suministro"]},"feedback_generico":"Se espera que describas un análisis del impacto, negociación, búsqueda de alternativas y medidas para mitigar el riesgo en la cadena de suministro."}'::jsonb
),
('PR', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica qué es el ROI y cómo se calcula.',
 '["Retorno de Inversión", "(Ganancia - Inversión) / Inversión"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{"frases_clave_esperadas":["retorno sobre la inversión","relación entre ganancia e inversión","ganancia menos inversión dividido por inversión"]},"feedback_generico":"Se espera que digas que el ROI es el retorno sobre la inversión y se calcula como (ganancia menos inversión) dividido por la inversión."}'::jsonb
);

-- 3. INGENIERÍA INFORMÁTICA (Código: PR)
INSERT INTO pregunta (
    tipo_banco, sector, nivel, meta_cargo,
    tipo_pregunta, texto, pistas,
    config_respuesta, config_evaluacion
) VALUES
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la unidad mínima de información en un computador?',
 '["0 o 1", "Bit"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Byte"},
    {"id":"B","texto":"Bit"},
    {"id":"C","texto":"Hertz"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"La unidad mínima de información es el bit, que representa un 0 o un 1.",
    "explicacion_incorrecta":"El byte agrupa varios bits y los Hertz miden frecuencia, no cantidad de información."
 }}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué sistema numérico utilizan internamente los computadores?',
 '["Base 2", "Ceros y unos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Decimal"},
    {"id":"B","texto":"Hexadecimal"},
    {"id":"C","texto":"Binario"}
  ],
  "respuesta_correcta":"C"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Los computadores representan la información internamente en sistema binario (base 2).",
    "explicacion_incorrecta":"Decimal y hexadecimal se usan para representación humana, pero internamente el hardware trabaja en binario."
 }}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 'Diferencia básica entre RAM y ROM.',
 '["Volátil vs No volátil", "Lectura/Escritura vs Solo lectura"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "RAM es memoria volátil",
      "ROM es no volátil",
      "RAM permite lectura y escritura",
      "ROM es principalmente solo lectura"
    ]
  },
  "feedback_generico":"Se espera que menciones que la RAM es volátil y de lectura/escritura, mientras que la ROM es no volátil y normalmente solo lectura."
 }'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la función principal de un Sistema Operativo?',
 '["Intermediario", "Gestión de recursos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Editar textos"},
    {"id":"B","texto":"Gestionar el hardware y proveer servicios a los programas"},
    {"id":"C","texto":"Navegar por internet"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"El sistema operativo gestiona el hardware y proporciona servicios a las aplicaciones.",
    "explicacion_incorrecta":"Editar textos o navegar son funciones de aplicaciones específicas, no del sistema operativo en sí."
 }}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Qué es una dirección IP?',
 '["Identificador de red", "Como un número de teléfono"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "identificador de un dispositivo en una red",
      "dirección lógica",
      "permite enrutar tráfico"
    ]
  },
  "feedback_generico":"Se espera que la definas como un identificador numérico que permite localizar y enrutar paquetes hacia un dispositivo en la red."
 }'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué significan las siglas CPU?',
 '["Cerebro del PC", "Central..."]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Central Processing Unit"},
    {"id":"B","texto":"Computer Personal Unit"},
    {"id":"C","texto":"Central Power Unit"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"CPU significa Central Processing Unit, la unidad central de procesamiento.",
    "explicacion_incorrecta":"No es una unidad personal ni de energía; se refiere al procesador principal del sistema."
 }}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 'En lógica booleana, ¿cuál es el resultado de 1 AND 0?',
 '["Ambos deben ser 1", "Multiplicación lógica"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"1"},
    {"id":"B","texto":"0"},
    {"id":"C","texto":"Null"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"En AND, el resultado es 1 solo si ambos operandos son 1; 1 AND 0 da 0.",
    "explicacion_incorrecta":"No se obtiene 1 si uno de los operandos es 0."
 }}'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Qué es el Hardware?',
 '["Parte física", "Lo que puedes tocar"]'::jsonb,
 '{"min_caracteres":10,"max_caracteres":150}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "parte física de un computador",
      "componentes que se pueden tocar",
      "dispositivos electrónicos"
    ]
  },
  "feedback_generico":"Se espera que digas que el hardware es la parte física del sistema, los componentes que se pueden tocar."
 }'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'abierta',
 '¿Para qué sirve un algoritmo?',
 '["Secuencia de pasos", "Resolver problemas"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "secuencia de pasos",
      "procedimiento definido",
      "resolver un problema",
      "alcanzar un objetivo"
    ]
 },
  "feedback_generico":"Se espera que lo definas como una secuencia finita de pasos para resolver un problema o realizar una tarea."
 }'::jsonb
),
('PR', 'TI', 'jr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es el componente encargado de los gráficos en un PC?',
 '["GPU", "Tarjeta..."]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"CPU"},
    {"id":"B","texto":"GPU"},
    {"id":"C","texto":"SSD"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"La GPU (tarjeta gráfica) es el componente especializado en procesamiento gráfico.",
    "explicacion_incorrecta":"La CPU es de propósito general y el SSD es almacenamiento, no procesan gráficos."
 }}'::jsonb
),

-- MID ------------------------------------------------------------------------
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 'Explica qué es la virtualización.',
 '["Máquinas virtuales", "Abstraer hardware"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "crear máquinas virtuales",
      "abstracción del hardware",
      "varios sistemas sobre el mismo hardware físico"
    ]
  },
  "feedback_generico":"Se espera que menciones que la virtualización permite ejecutar múltiples entornos aislados sobre el mismo hardware físico mediante una capa de abstracción."
 }'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿En qué capa del modelo OSI funciona el protocolo IP?',
 '["Red", "Capa 3"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Capa 2 (Enlace)"},
    {"id":"B","texto":"Capa 3 (Red)"},
    {"id":"C","texto":"Capa 4 (Transporte)"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"IP opera en la capa 3 del modelo OSI, la capa de red.",
    "explicacion_incorrecta":"La capa 2 se encarga de enlace de datos y la capa 4 de transporte (TCP/UDP)."
 }}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es RAID 1 y para qué sirve?',
 '["Espejo", "Redundancia"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "espejo de discos",
      "misma información en dos discos",
      "redundancia de datos",
      "tolerancia a fallos"
    ]
  },
  "feedback_generico":"Se espera que expliques que RAID 1 duplica la información en dos discos (espejado) para lograr redundancia y tolerancia a fallos."
 }'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 'Diferencia entre TCP y UDP.',
 '["Fiabilidad vs Velocidad", "Conexión vs Sin conexión"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"TCP es más rápido, UDP es seguro"},
    {"id":"B","texto":"TCP garantiza entrega (orientado a conexión), UDP no (streaming)"},
    {"id":"C","texto":"Son iguales"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"TCP es orientado a conexión y garantiza entrega y orden; UDP es más ligero y no garantiza entrega ni orden.",
    "explicacion_incorrecta":"No son iguales ni TCP es simplemente más rápido; UDP suele ser más rápido al no ofrecer garantías."
 }}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es la Normalización en Bases de Datos?',
 '["Evitar redundancia", "Formas normales"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "proceso de organizar tablas",
      "reducir redundancia",
      "mejorar integridad de los datos",
      "formas normales"
    ]
  },
  "feedback_generico":"Se espera que describas la normalización como el proceso de estructurar una base de datos para minimizar redundancia y mejorar la integridad mediante formas normales."
 }'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿Qué función cumple un servidor DNS?',
 '["Traduce nombres a IP", "Directorio telefónico de internet"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Asigna IPs dinámicas"},
    {"id":"B","texto":"Traduce nombres de dominio a direcciones IP"},
    {"id":"C","texto":"Encripta la conexión"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"DNS traduce nombres de dominio legibles por humanos en direcciones IP.",
    "explicacion_incorrecta":"La asignación dinámica de IPs la hace DHCP y el cifrado lo realizan otros protocolos como TLS."
 }}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 'Describe el concepto de "Cloud Computing".',
 '["Servicios a través de internet", "Bajo demanda"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "recursos informáticos como servicio",
      "a través de internet",
      "bajo demanda",
      "pago por uso"
    ]
  },
  "feedback_generico":"Se espera que menciones que es la entrega de recursos de computación (servidores, almacenamiento, etc.) como servicios bajo demanda a través de internet."
 }'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 '¿Qué es un Firewall?',
 '["Cortafuegos", "Seguridad de red"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Un antivirus"},
    {"id":"B","texto":"Sistema que controla el tráfico de red entrante y saliente"},
    {"id":"C","texto":"Un cable de red blindado"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Un firewall controla y filtra el tráfico de red según reglas de seguridad.",
    "explicacion_incorrecta":"No es un antivirus ni un simple componente físico como un cable."
 }}'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'abierta',
 '¿Qué es el Kernel de un Sistema Operativo?',
 '["Núcleo", "Control directo del hardware"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "núcleo del sistema operativo",
      "gestiona recursos de hardware",
      "capa más baja",
      "intermediario entre hardware y resto del sistema"
    ]
  },
  "feedback_generico":"Se espera que definas el kernel como el núcleo del sistema operativo que gestiona directamente el hardware y los recursos básicos."
 }'::jsonb
),
('PR', 'TI', 'mid', 'Devops Engineer', 'opcion_multiple',
 'En criptografía asimétrica, ¿qué clave se comparte públicamente?',
 '["Pública vs Privada", "Para encriptar o verificar"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Clave Privada"},
    {"id":"B","texto":"Clave Pública"},
    {"id":"C","texto":"Ninguna"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"En criptografía asimétrica la clave pública se comparte; la privada se mantiene en secreto.",
    "explicacion_incorrecta":"Compartir la clave privada comprometería la seguridad del sistema."
 }}'::jsonb
),

-- SR -------------------------------------------------------------------------
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'Diseña una arquitectura de Alta Disponibilidad (HA) básica para una web crítica.',
 '["Balanceadores", "Redundancia", "Multi-AZ"]'::jsonb,
 '{"min_caracteres":80,"max_caracteres":1000}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "balanceador de carga",
      "múltiples instancias",
      "redundancia",
      "múltiples zonas de disponibilidad o data centers",
      "eliminar puntos únicos de fallo"
    ]
  },
  "feedback_generico":"Se espera que describas balanceadores de carga, instancias redundantes en varias zonas o data centers y ausencia de puntos únicos de fallo."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'Explica el funcionamiento de un ataque DDoS y cómo mitigarlo.',
 '["Denegación distribuida", "CDN, WAF"]'::jsonb,
 '{"min_caracteres":60,"max_caracteres":800}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "muchos orígenes atacan un mismo objetivo",
      "saturar recursos o ancho de banda",
      "mitigación con WAF",
      "CDN",
      "rate limiting",
      "filtrado de tráfico"
    ]
  },
  "feedback_generico":"Se espera que menciones que un DDoS es un ataque distribuido para saturar un servicio y que la mitigación incluye WAF, CDN, filtrado y limitación de tráfico."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 '¿Qué es un Container Orchestrator (ej: Kubernetes) y por qué es necesario en grandes sistemas?',
 '["Gestión de ciclo de vida", "Escalado automático"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Es un antivirus para contenedores"},
    {"id":"B","texto":"Automatiza el despliegue, escalado y gestión de aplicaciones en contenedores"},
    {"id":"C","texto":"Es un lenguaje de programación"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Un orquestador automatiza despliegue, escalado, recuperación y gestión del ciclo de vida de contenedores.",
    "explicacion_incorrecta":"No es un antivirus ni un lenguaje de programación."
 }}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 'Diferencia entre Escalado Vertical y Horizontal.',
 '["Más potencia vs Más máquinas", "CPU vs Nodos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Vertical es agregar más máquinas, Horizontal es mejorar la máquina"},
    {"id":"B","texto":"Vertical es mejorar la máquina (más RAM/CPU), Horizontal es agregar más máquinas"},
    {"id":"C","texto":"Son lo mismo"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"El escalado vertical aumenta recursos de una máquina; el horizontal añade más máquinas o instancias.",
    "explicacion_incorrecta":"No son lo mismo y la opción A invierte las definiciones."
 }}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es "Infrastructure as Code" (IaC)?',
 '["Terraform, Ansible", "Infraestructura programable"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "definir infraestructura mediante código",
      "automatizar despliegues",
      "versionar la infraestructura",
      "herramientas como Terraform o Ansible"
    ]
  },
  "feedback_generico":"Se espera que menciones que IaC consiste en describir y gestionar la infraestructura mediante código versionable y automatizable."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 'En el contexto de Big Data, explica las 3 V.',
 '["Volumen, Velocidad, Variedad", "Datos masivos"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "volumen",
      "velocidad",
      "variedad",
      "datos masivos"
    ]
  },
  "feedback_generico":"Se espera que identifiques las tres V clásicas de Big Data: volumen, velocidad y variedad de los datos."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es un plan de DRP (Disaster Recovery Plan)?',
 '["Recuperación ante desastres", "Continuidad de negocio"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":600}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "plan de recuperación ante desastres",
      "restaurar servicios",
      "minimizar tiempo de inactividad",
      "continuidad de negocio"
    ]
 },
  "feedback_generico":"Se espera que digas que es un plan documentado para recuperar sistemas y servicios tras un desastre y asegurar la continuidad del negocio."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 'Explica el concepto de "Zero Trust Security".',
 '["No confiar en nadie", "Verificar siempre"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Confiar solo en la red interna"},
    {"id":"B","texto":"Modelo donde no se confía en ningún usuario o dispositivo, dentro o fuera del perímetro"},
    {"id":"C","texto":"No usar contraseñas"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Zero Trust parte de no confiar por defecto en ningún usuario o dispositivo, verificando siempre y aplicando mínimos privilegios.",
    "explicacion_incorrecta":"No consiste en confiar en la red interna ni en eliminar contraseñas sin otras medidas de autenticación."
 }}'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'abierta',
 '¿Qué es Latencia y cómo afecta a los sistemas distribuidos?',
 '["Retardo", "Tiempo de viaje del paquete"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "tiempo que tarda un mensaje en ir de origen a destino",
      "retardo de comunicación",
      "impacta en tiempos de respuesta",
      "importante en sistemas distribuidos"
    ]
  },
  "feedback_generico":"Se espera que definas la latencia como el retardo en la comunicación y expliques que aumenta los tiempos de respuesta en sistemas distribuidos."
 }'::jsonb
),
('PR', 'TI', 'sr', 'Devops Engineer', 'opcion_multiple',
 '¿Cuál es la principal ventaja de usar una arquitectura "Serverless"?',
 '["No gestionas servidores", "Pago por uso"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Mayor control del hardware"},
    {"id":"B","texto":"Abstracción total del servidor y modelo de costos por ejecución"},
    {"id":"C","texto":"Es gratis"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Serverless abstrae la gestión de servidores y cobra típicamente por ejecución o consumo real.",
    "explicacion_incorrecta":"No da más control del hardware ni implica que el servicio sea gratuito."
 }}'::jsonb
);


-- 4. DESARROLLADOR (Código: PR)
INSERT INTO pregunta (
    tipo_banco, sector, nivel, meta_cargo,
    tipo_pregunta, texto, pistas,
    config_respuesta, config_evaluacion
) VALUES
-- JR -------------------------------------------------------------------------
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué imprime "console.log(typeof [])" en JavaScript?',
 '["Arrays son objetos", "Curiosidad de JS"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"array"},
    {"id":"B","texto":"object"},
    {"id":"C","texto":"list"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"En JavaScript los arrays son un tipo especial de objeto, por eso typeof [] devuelve \"object\".",
    "explicacion_incorrecta":"Aunque los arrays se usan como listas, a nivel interno siguen siendo objetos en JavaScript."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Para qué sirve el operador "++" en muchos lenguajes?',
 '["Incremento", "Sumar uno"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Suma dos variables"},
    {"id":"B","texto":"Incrementa el valor de la variable en 1"},
    {"id":"C","texto":"Concatena strings"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"El operador ++ incrementa el valor numérico de la variable en una unidad.",
    "explicacion_incorrecta":"No suma dos variables ni concatena cadenas, solo incrementa el valor de una variable."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un bucle "infinito"?',
 '["Nunca termina", "Condición siempre true"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "nunca termina",
      "condición siempre verdadera",
      "no alcanza una condición de salida",
      "se ejecuta indefinidamente"
    ]
  },
  "feedback_generico":"Se espera que definas un bucle que nunca termina porque su condición de salida nunca se cumple (siempre verdadera o mal diseñada)."
 }'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 'En Git, ¿qué comando descarga los cambios del remoto al local?',
 '["Traer cambios", "Pull..."]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"git push"},
    {"id":"B","texto":"git pull"},
    {"id":"C","texto":"git commit"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"git pull descarga los cambios del remoto y los integra en la rama local.",
    "explicacion_incorrecta":"git push envía cambios al remoto y git commit solo registra cambios en el repositorio local."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es una variable?',
 '["Espacio de memoria", "Contenedor"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "espacio de memoria",
      "contiene un valor",
      "identificador asociado a un dato"
    ]
  },
  "feedback_generico":"Se espera que expliques que una variable es un espacio de memoria identificado por un nombre donde se almacena un valor."
 }'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 'En CSS, ¿qué propiedad cambia el color de fondo?',
 '["Background...", "Color es para texto"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"color"},
    {"id":"B","texto":"background-color"},
    {"id":"C","texto":"border"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"La propiedad background-color define el color de fondo de un elemento.",
    "explicacion_incorrecta":"La propiedad color afecta al texto, no al fondo del elemento."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es el DOM en desarrollo web?',
 '["Document Object Model", "Árbol de elementos"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "Document Object Model",
      "representación en árbol",
      "nodos y elementos",
      "estructura del documento HTML"
    ]
  },
  "feedback_generico":"Se espera que menciones que el DOM es una representación en árbol del documento HTML que permite manipular sus elementos con código."
 }'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Cuál es el índice del primer elemento en un array (en la mayoría de lenguajes)?',
 '["Empieza en...", "Cero"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"0"},
    {"id":"B","texto":"1"},
    {"id":"C","texto":"-1"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"En muchos lenguajes el primer elemento de un array está en el índice 0.",
    "explicacion_incorrecta":"El índice 1 suele ser el segundo elemento, y -1 no es un índice estándar en la mayoría de lenguajes."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué significa IDE?',
 '["Entorno de Desarrollo", "Integrated..."]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Integrated Development Environment"},
    {"id":"B","texto":"Internet Development Explorer"},
    {"id":"C","texto":"Internal Data Exchange"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"IDE significa Integrated Development Environment, un entorno integrado para desarrollar software.",
    "explicacion_incorrecta":"No es un navegador ni un formato de intercambio de datos."
 }}'::jsonb
),
('PR', 'Desarrollador', 'jr', 'Desarrollor FullStack', 'abierta',
 'Escribe una función simple que sume dos números (pseudocódigo).',
 '["function suma(a,b)...", "return..."]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "función que recibe dos parámetros",
      "retorna la suma",
      "a + b"
    ]
  },
  "feedback_generico":"Se espera algo del tipo: function suma(a, b) { return a + b; } o un pseudocódigo equivalente."
 }'::jsonb
),

-- MID ------------------------------------------------------------------------
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la Inyección de Dependencias?',
 '["Patrón de diseño", "Inversión de control"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "patrón de diseño",
      "inyectar dependencias desde fuera",
      "inversión de control",
      "facilita pruebas y desacoplamiento"
    ]
  },
  "feedback_generico":"Se espera que expliques que las dependencias se entregan desde fuera de la clase, invirtiendo el control y reduciendo el acoplamiento."
 }'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'En una API REST, ¿qué verbo HTTP se usa para actualizar parcialmente un recurso?',
 '["No es PUT", "Parcial"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"PUT"},
    {"id":"B","texto":"PATCH"},
    {"id":"C","texto":"POST"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"PATCH se usa típicamente para actualizaciones parciales de un recurso.",
    "explicacion_incorrecta":"PUT suele reemplazar el recurso completo; POST se usa para crear o acciones específicas."
 }}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 'Explica el concepto de "Callback" en programación asíncrona.',
 '["Función pasada como argumento", "Se ejecuta después"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "función pasada como argumento",
      "se ejecuta después de que ocurra un evento",
      "tras completar una operación asíncrona"
    ]
  },
  "feedback_generico":"Se espera que digas que un callback es una función que se pasa como argumento y se ejecuta cuando termina una operación o evento."
 }'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué diferencia hay entre "git merge" y "git rebase"?',
 '["Historial lineal vs Historial ramificado", "Reescritura"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Merge reescribe la historia, Rebase crea un commit de unión"},
    {"id":"B","texto":"Rebase reescribe la historia linealmente, Merge crea un commit de unión"},
    {"id":"C","texto":"Son idénticos"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Rebase reescribe la historia para hacerla lineal; merge crea un commit de unión entre ramas.",
    "explicacion_incorrecta":"No son idénticos y el merge no reescribe el historial existente."
 }}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un ORM?',
 '["Object Relational Mapping", "Base de datos como objetos"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "Object Relational Mapping",
      "mapear tablas a objetos",
      "operar la base de datos desde código orientado a objetos"
    ]
  },
  "feedback_generico":"Se espera que definas un ORM como una capa que mapea tablas y filas a clases y objetos para trabajar con la base de datos de forma más declarativa."
 }'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'En POO, ¿qué es el Polimorfismo?',
 '["Muchas formas", "Mismo método, diferente comportamiento"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"La capacidad de heredar atributos"},
    {"id":"B","texto":"Capacidad de objetos de diferentes clases de responder al mismo mensaje de distinta manera"},
    {"id":"C","texto":"Ocultar datos privados"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"El polimorfismo permite que distintos tipos respondan de forma diferente a la misma interfaz o mensaje.",
    "explicacion_incorrecta":"No es simplemente herencia ni encapsulamiento."
 }}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es el "Scope" (alcance) de una variable?',
 '["Dónde vive la variable", "Global vs Local"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "ámbito donde existe la variable",
      "dónde es accesible",
      "global o local",
      "bloque o función"
    ]
  },
  "feedback_generico":"Se espera que expliques que el scope define en qué parte del código es visible y accesible una variable."
 }'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Por qué usarías Docker en desarrollo?',
 '["Entornos consistentes", "Funciona en mi máquina"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Para hacer el código más rápido"},
    {"id":"B","texto":"Para garantizar paridad entre entornos de desarrollo y producción"},
    {"id":"C","texto":"Para diseñar interfaces"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Docker ayuda a tener entornos consistentes entre desarrollo, pruebas y producción.",
    "explicacion_incorrecta":"No está pensado directamente para acelerar el código ni para diseñar interfaces."
 }}'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'abierta',
 '¿Qué es MVC?',
 '["Modelo Vista Controlador", "Patrón de arquitectura"]'::jsonb,
 '{"min_caracteres":20,"max_caracteres":200}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "Modelo Vista Controlador",
      "separa lógica de negocio y presentación",
      "patrón de arquitectura"
    ]
  },
  "feedback_generico":"Se espera que digas que MVC es un patrón de arquitectura que separa el Modelo, la Vista y el Controlador."
 }'::jsonb
),
('PR', 'Desarrollador', 'mid', 'Desarrollor FullStack', 'opcion_multiple',
 'Identifica el error: "SELECT * FROM users WHERE name = ''Pepe"',
 '["Faltan comillas", "Sintaxis SQL"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Falta cerrar la comilla simple"},
    {"id":"B","texto":"Falta el punto y coma"},
    {"id":"C","texto":"Users va con mayúscula"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"La cadena de texto no está bien cerrada; falta una comilla simple al final.",
    "explicacion_incorrecta":"El punto y coma es opcional y el uso de mayúsculas en el nombre de tabla no es un error sintáctico."
 }}'::jsonb
),

-- SR -------------------------------------------------------------------------
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'Explica qué es una "Race Condition" (Condición de Carrera).',
 '["Concurrencia", "Resultados impredecibles"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "acceso concurrente",
      "orden de ejecución afecta al resultado",
      "resultados impredecibles",
      "recursos compartidos"
    ]
  },
  "feedback_generico":"Se espera que menciones que ocurre cuando dos o más hilos o procesos acceden a recursos compartidos y el resultado depende del orden de ejecución."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'En Arquitectura de Software, ¿qué es el patrón Singleton y cuándo es peligroso?',
 '["Instancia única", "Estado global mutable"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":500}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "una sola instancia",
      "punto global de acceso",
      "acoplamiento fuerte",
      "dificulta pruebas",
      "problemas de concurrencia"
    ]
  },
  "feedback_generico":"Se espera que expliques que Singleton limita a una sola instancia global y que puede ser peligroso por introducir estado global, acoplamiento y problemas de pruebas o concurrencia."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué principio SOLID se viola si una clase tiene demasiadas responsabilidades?',
 '["Single Responsibility", "La S de SOLID"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"SRP (Single Responsibility Principle)"},
    {"id":"B","texto":"OCP (Open/Closed Principle)"},
    {"id":"C","texto":"LSP (Liskov Substitution Principle)"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"Si una clase hace demasiadas cosas viola el principio de responsabilidad única (SRP).",
    "explicacion_incorrecta":"OCP y LSP tratan de extensibilidad y sustitución, no de cuántas responsabilidades tiene una clase."
 }}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es un "Memory Leak" y cómo lo detectas?',
 '["Fuga de memoria", "El consumo de RAM crece sin parar"]'::jsonb,
 '{"min_caracteres":50,"max_caracteres":600}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "fuga de memoria",
      "memoria que no se libera",
      "crecimiento constante de uso de memoria",
      "herramientas de profiling"
    ]
  },
  "feedback_generico":"Se espera que definas el memory leak como memoria que no se libera nunca y que comentes que se detecta observando el crecimiento de RAM o usando herramientas de profiling."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 'Comparación: Monolito vs Microservicios. ¿Cuándo NO usarías microservicios?',
 '["Complejidad", "Equipos pequeños"]'::jsonb,
 '{"min_caracteres":60,"max_caracteres":800}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "sistema pequeño o simple",
      "equipo reducido",
      "coste de la complejidad",
      "overengineering"
    ]
  },
  "feedback_generico":"Se espera que digas que no conviene usar microservicios en sistemas sencillos o con equipos pequeños donde la complejidad extra no se justifica."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 'En bases de datos, ¿qué es una transacción ACID?',
 '["Atomicidad, Consistencia...", "Todo o nada"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Un tipo de base de datos NoSQL"},
    {"id":"B","texto":"Un conjunto de propiedades que garantizan la validez de las transacciones"},
    {"id":"C","texto":"Un virus informático"}
  ],
  "respuesta_correcta":"B"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"ACID describe propiedades (Atomicidad, Consistencia, Aislamiento, Durabilidad) que garantizan transacciones fiables.",
    "explicacion_incorrecta":"No es un tipo de base de datos ni un malware."
 }}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la complejidad ciclomática?',
 '["Métrica de código", "Caminos independientes"]'::jsonb,
 '{"min_caracteres":30,"max_caracteres":300}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "mide los caminos independientes de un código",
      "métrica de complejidad",
      "relacionada con número de decisiones"
    ]
  },
  "feedback_generico":"Se espera que digas que es una métrica que mide el número de caminos independientes en el código y por tanto su complejidad."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 'Estrategias de Caché: Diferencia entre Cache-Aside y Write-Through.',
 '["Lectura vs Escritura", "Quién carga los datos"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Cache-Aside la app carga los datos si no están; Write-Through escribe en caché y DB a la vez"},
    {"id":"B","texto":"Son lo mismo"},
    {"id":"C","texto":"Write-Through es solo para lectura"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"En Cache-Aside la aplicación lee de la caché y si no hay dato lo carga de la base; en Write-Through se escribe en caché y base de datos a la vez.",
    "explicacion_incorrecta":"No son lo mismo y Write-Through no es una estrategia solo de lectura."
 }}'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'abierta',
 '¿Qué es la Idempotencia en una API REST?',
 '["Repetir la llamada", "Mismo resultado"]'::jsonb,
 '{"min_caracteres":40,"max_caracteres":400}'::jsonb,
 '{"tipo_item":"open","nlp":{
    "frases_clave_esperadas":[
      "mismo resultado al repetir la misma petición",
      "operación que no cambia el estado más de una vez",
      "repetir la llamada no debe tener efectos adicionales"
    ]
  },
  "feedback_generico":"Se espera que expliques que una operación idempotente produce el mismo resultado aunque se ejecute varias veces con los mismos datos."
 }'::jsonb
),
('PR', 'Desarrollador', 'sr', 'Desarrollor FullStack', 'opcion_multiple',
 '¿Qué es el teorema CAP?',
 '["Distribuido", "Escoge 2 de 3"]'::jsonb,
 '{"opciones":[
    {"id":"A","texto":"Consistency, Availability, Partition Tolerance"},
    {"id":"B","texto":"Capacity, Availability, Performance"},
    {"id":"C","texto":"Code, App, Program"}
  ],
  "respuesta_correcta":"A"
 }'::jsonb,
 '{"tipo_item":"choice","nlp":{
    "explicacion_correcta":"El teorema CAP afirma que en sistemas distribuidos solo se pueden garantizar a la vez dos de las tres propiedades: consistencia, disponibilidad y tolerancia a particiones.",
    "explicacion_incorrecta":"No se refiere a capacidad ni a rendimiento, sino a propiedades teóricas de sistemas distribuidos."
 }}'::jsonb
);



-- ====================================================================================
-- SOPORTE TI (5 preguntas - nivel básico) -- NV
-- ====================================================================================
INSERT INTO pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta, config_evaluacion) VALUES
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un sistema operativo?',
 '["Windows, Linux, macOS", "Software base"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un programa que gestiona el hardware y software del computador"},
   {"id":"B", "texto":"Un antivirus"},
   {"id":"C", "texto":"Una aplicación de office"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un sistema operativo es el software fundamental que gestiona el hardware y software del computador, permitiendo que las aplicaciones funcionen.","explicacion_incorrecta":"No es un antivirus ni una aplicación de office, sino el software base que permite que todo funcione."}}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué significa IP en redes?',
 '["Dirección de red", "Internet Protocol"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Internet Provider"},
   {"id":"B", "texto":"Internet Protocol"},
   {"id":"C", "texto":"Internal Program"}
 ], "respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"IP significa Internet Protocol, el protocolo fundamental para la comunicación en redes que define cómo se direccionan y transmiten los datos.","explicacion_incorrecta":"No es Internet Provider ni Internal Program, sino el protocolo estándar de comunicación en redes."}}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Cuál es la función del protocolo DHCP?',
 '["Asigna direcciones", "Automático"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Asignar direcciones IP automáticamente"},
   {"id":"B", "texto":"Proteger contra virus"},
   {"id":"C", "texto":"Comprimir archivos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"DHCP (Dynamic Host Configuration Protocol) asigna automáticamente direcciones IP y configuración de red a los dispositivos, facilitando la administración de redes.","explicacion_incorrecta":"No es para protección contra virus ni compresión de archivos, sino para automatizar la asignación de direcciones IP."}}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué comando usarías para verificar la conectividad de red en Windows?',
 '["Verificar conexión", "Ping..."]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"ipconfig"},
   {"id":"B", "texto":"ping"},
   {"id":"C", "texto":"netstat"}
 ], "respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El comando ping verifica la conectividad de red enviando paquetes ICMP a un host destino y esperando respuesta.","explicacion_incorrecta":"ipconfig muestra configuración de red y netstat muestra conexiones activas, pero ping es específico para verificar conectividad."}}'::jsonb
),
('NV', 'TI', 'jr', 'Soporte TI', 'opcion_multiple',
 '¿Qué es un firewall?',
 '["Protección de red", "Bloquea tráfico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un sistema que controla el tráfico de red entrante y saliente"},
   {"id":"B", "texto":"Un tipo de cable de red"},
   {"id":"C", "texto":"Un servidor web"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un firewall es un sistema de seguridad que controla y filtra el tráfico de red entrante y saliente según reglas de seguridad predefinidas.","explicacion_incorrecta":"No es un cable de red ni un servidor web, sino un componente de seguridad que protege la red."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Docker es una plataforma de contenedores que permite empaquetar aplicaciones con todas sus dependencias en unidades portables y aisladas.","explicacion_incorrecta":"No es un lenguaje de programación ni una base de datos, sino una plataforma para crear y ejecutar contenedores."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Qué es CI/CD?',
 '["Integración continua", "Despliegue continuo"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Continuous Integration/Continuous Deployment"},
   {"id":"B", "texto":"Central Information Control Data"},
   {"id":"C", "texto":"Computer Integration Code Development"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"CI/CD significa Continuous Integration/Continuous Deployment, prácticas que automatizan la integración de código y su despliegue a producción.","explicacion_incorrecta":"No se refiere a control de datos ni desarrollo de código, sino a la automatización del ciclo de entrega de software."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Qué es Kubernetes?',
 '["Orquestación", "K8s"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un sistema de orquestación de contenedores"},
   {"id":"B", "texto":"Un editor de código"},
   {"id":"C", "texto":"Un framework de testing"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Kubernetes (K8s) es un sistema de orquestación de contenedores que automatiza el despliegue, escalado y gestión de aplicaciones contenerizadas.","explicacion_incorrecta":"No es un editor de código ni un framework de testing, sino una plataforma para orquestar contenedores a escala."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'DevOps Engineer', 'opcion_multiple',
 '¿Para qué sirve Terraform?',
 '["Infrastructure as Code", "IaC"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para definir infraestructura como código"},
   {"id":"B", "texto":"Para compilar código"},
   {"id":"C", "texto":"Para hacer testing"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Terraform es una herramienta de Infrastructure as Code (IaC) que permite definir, provisionar y gestionar infraestructura mediante código declarativo.","explicacion_incorrecta":"No es para compilar código ni para testing, sino para automatizar la creación y gestión de infraestructura."}}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'DevOps Engineer', 'abierta',
 '¿Qué es una pipeline de CI/CD?',
 '["Automatización", "Build, test, deploy"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"Una pipeline de CI/CD es una secuencia automatizada de pasos que incluye compilación, pruebas y despliegue del código, permitiendo entregas rápidas y confiables.","explicacion_incorrecta":"No es simplemente un proceso manual ni una herramienta específica, sino un flujo automatizado completo desde el código hasta producción."},"feedback_generico":"Se espera que menciones la automatización del proceso de build, test y deploy del código."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un servidor es un computador diseñado para proveer servicios, recursos o datos a otros equipos (clientes) en una red.","explicacion_incorrecta":"No es un cable ni una aplicación móvil, sino un equipo dedicado a servir recursos a otros dispositivos."}}'::jsonb
),
('NV', 'TI', 'jr', 'SysAdmin', 'opcion_multiple',
 '¿Qué comando en Linux muestra los procesos en ejecución?',
 '["Ver procesos", "ps, top"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"ls"},
   {"id":"B", "texto":"ps"},
   {"id":"C", "texto":"cd"}
 ], "respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El comando ps (process status) muestra información sobre los procesos activos en el sistema Linux.","explicacion_incorrecta":"ls lista archivos y cd cambia de directorio, pero ps es el comando específico para ver procesos."}}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
 '¿Qué es un backup incremental?',
 '["Solo cambios", "Vs completo"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Copia solo los cambios desde el último backup"},
   {"id":"B", "texto":"Copia todos los archivos siempre"},
   {"id":"C", "texto":"Elimina archivos antiguos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un backup incremental copia solo los archivos que han cambiado desde el último backup, ahorrando tiempo y espacio de almacenamiento.","explicacion_incorrecta":"No copia todo ni elimina archivos, solo respalda los cambios nuevos desde el último backup."}}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
 '¿Qué puerto usa SSH por defecto?',
 '["Secure Shell", "22"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"80"},
   {"id":"B", "texto":"22"},
   {"id":"C", "texto":"443"}
 ], "respuesta_correcta":"B"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"SSH (Secure Shell) usa el puerto 22 por defecto para conexiones seguras remotas.","explicacion_incorrecta":"El puerto 80 es para HTTP y 443 para HTTPS, mientras que SSH usa el puerto 22."}}'::jsonb
),
('NV', 'TI', 'mid', 'SysAdmin', 'abierta',
 'Explica qué es un RAID y para qué sirve',
 '["Redundancia", "Varios discos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"RAID (Redundant Array of Independent Disks) combina múltiples discos duros para mejorar el rendimiento y/o proporcionar redundancia de datos, protegiendo contra fallos de disco.","explicacion_incorrecta":"No es simplemente juntar discos, sino configurarlos estratégicamente para redundancia o rendimiento."},"feedback_generico":"Se espera que menciones que RAID combina varios discos para redundancia y/o mejor rendimiento."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"API significa Application Programming Interface, un conjunto de reglas y definiciones que permite la comunicación entre diferentes aplicaciones de software.","explicacion_incorrecta":"No es información de programa avanzada ni integración de procesos, sino una interfaz estándar para que las aplicaciones se comuniquen."}}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Backend', 'opcion_multiple',
 '¿Qué es REST?',
 '["Arquitectura de APIs", "HTTP"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un estilo arquitectónico para APIs web"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un lenguaje de programación"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"REST (Representational State Transfer) es un estilo arquitectónico para diseñar APIs web que utiliza HTTP y sus métodos estándar.","explicacion_incorrecta":"No es una base de datos ni un lenguaje de programación, sino un estilo arquitectónico para diseñar servicios web."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Backend', 'abierta',
 '¿Qué diferencia hay entre SQL y NoSQL?',
 '["Estructurado vs No estructurado", "Relacional vs Documental"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"SQL son bases de datos relacionales con esquema fijo y tablas relacionadas, mientras que NoSQL son bases de datos no relacionales con esquemas flexibles, diseñadas para datos no estructurados y escalabilidad horizontal.","explicacion_incorrecta":"No es solo una cuestión de nombre, sino diferencias fundamentales en estructura, escalabilidad y casos de uso."},"feedback_generico":"Se espera que menciones las diferencias en estructura (relacional vs no relacional) y flexibilidad de esquema."}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Backend', 'opcion_multiple',
 '¿Qué es un middleware?',
 '["Intermediario", "Entre request y response"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Software que procesa peticiones entre cliente y servidor"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un framework frontend"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un middleware es software que actúa como intermediario procesando peticiones entre el cliente y el servidor, permitiendo funcionalidades como autenticación, logging o validación.","explicacion_incorrecta":"No es una base de datos ni un framework frontend, sino una capa intermedia de procesamiento de peticiones."}}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Backend', 'abierta',
 'Explica el patrón Repository en arquitectura de software',
 '["Separación de concerns", "Acceso a datos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El patrón Repository abstrae la capa de acceso a datos, proporcionando una interfaz para operaciones CRUD sin exponer los detalles de implementación de la base de datos, mejorando la separación de responsabilidades y facilitando el testing.","explicacion_incorrecta":"No es simplemente acceder a la base de datos directamente, sino crear una capa de abstracción que separa la lógica de negocio del acceso a datos."},"feedback_generico":"Se espera que menciones la abstracción del acceso a datos y la separación de responsabilidades."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"HTML significa HyperText Markup Language, el lenguaje estándar de marcado para crear la estructura y contenido de páginas web.","explicacion_incorrecta":"No es un lenguaje de tecnología moderna ni una herramienta casera, sino el lenguaje fundamental para estructurar contenido web."}}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Para qué sirve CSS?',
 '["Estilos", "Diseño visual"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para dar estilos y diseño a páginas web"},
   {"id":"B", "texto":"Para programar la lógica"},
   {"id":"C", "texto":"Para bases de datos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"CSS (Cascading Style Sheets) se utiliza para definir la presentación visual, estilos y diseño de páginas web, separando el contenido de su apariencia.","explicacion_incorrecta":"No es para programar lógica ni gestionar bases de datos, sino exclusivamente para el diseño visual y estilos."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Qué es el DOM?',
 '["Document Object Model", "Árbol de elementos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Document Object Model - representación de la página"},
   {"id":"B", "texto":"Data Operation Method"},
   {"id":"C", "texto":"Digital Online Manager"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El DOM (Document Object Model) es una representación en forma de árbol de la estructura de una página web que permite a JavaScript interactuar y manipular los elementos HTML dinámicamente.","explicacion_incorrecta":"No es un método de operación de datos ni un gestor digital, sino la representación programática del documento HTML."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
 '¿Qué es React?',
 '["Librería JS", "Componentes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Una librería de JavaScript para construir interfaces"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un servidor web"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"React es una librería de JavaScript desarrollada por Facebook para construir interfaces de usuario interactivas mediante componentes reutilizables.","explicacion_incorrecta":"No es una base de datos ni un servidor web, sino una librería específica para crear interfaces de usuario."}}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Frontend', 'abierta',
 'Explica qué es el Virtual DOM y por qué React lo usa',
 '["Rendimiento", "Comparación"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El Virtual DOM es una representación ligera en memoria del DOM real. React lo usa para optimizar el rendimiento al comparar cambios en el Virtual DOM antes de actualizar el DOM real, minimizando las manipulaciones costosas y mejorando la velocidad de renderizado.","explicacion_incorrecta":"No es simplemente una copia del DOM, sino una estrategia de optimización que permite actualizaciones eficientes mediante comparación y actualización selectiva."},"feedback_generico":"Se espera que menciones la optimización de rendimiento mediante comparación de cambios antes de actualizar el DOM real."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un desarrollador Full Stack trabaja tanto en frontend (interfaz de usuario) como en backend (servidor, base de datos, lógica de negocio), dominando el stack completo de tecnologías.","explicacion_incorrecta":"No es un especialista solo en bases de datos o diseño, sino alguien con habilidades en todas las capas del desarrollo web."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es Node.js?',
 '["JavaScript en servidor", "Runtime"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un entorno de ejecución de JavaScript en el servidor"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un framework de CSS"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Node.js es un entorno de ejecución que permite ejecutar JavaScript en el servidor, construido sobre el motor V8 de Chrome, ideal para aplicaciones escalables y en tiempo real.","explicacion_incorrecta":"No es una base de datos ni un framework de CSS, sino un runtime para ejecutar JavaScript fuera del navegador."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es una SPA (Single Page Application)?',
 '["Una sola página", "Carga dinámica"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Aplicación que carga una sola página y actualiza contenido dinámicamente"},
   {"id":"B", "texto":"Aplicación con muchas páginas"},
   {"id":"C", "texto":"Aplicación móvil"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Una SPA (Single Page Application) carga una sola página HTML inicial y actualiza el contenido dinámicamente mediante JavaScript sin recargar la página completa, mejorando la experiencia del usuario.","explicacion_incorrecta":"No es una aplicación con muchas páginas ni necesariamente una app móvil, sino una aplicación web que funciona en una sola página."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
 '¿Qué es CORS?',
 '["Cross-Origin", "Seguridad"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Cross-Origin Resource Sharing - mecanismo de seguridad"},
   {"id":"B", "texto":"Central Online Resource System"},
   {"id":"C", "texto":"Computer Operating Resource Server"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"CORS (Cross-Origin Resource Sharing) es un mecanismo de seguridad del navegador que controla cómo recursos de un dominio pueden ser solicitados desde otro dominio, protegiendo contra ataques.","explicacion_incorrecta":"No es un sistema central de recursos ni un servidor de recursos, sino un mecanismo de seguridad para peticiones cross-origin."}}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Fullstack', 'abierta',
 'Explica la diferencia entre autenticación y autorización',
 '["Quién eres vs Qué puedes hacer", "Login vs Permisos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"La autenticación verifica quién eres (login con credenciales), mientras que la autorización determina qué puedes hacer (permisos y roles). La autenticación ocurre primero y la autorización después, controlando el acceso a recursos específicos.","explicacion_incorrecta":"No son lo mismo ni intercambiables; la autenticación es verificar identidad y la autorización es verificar permisos."},"feedback_generico":"Se espera que menciones que autenticación verifica identidad (quién eres) y autorización verifica permisos (qué puedes hacer)."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Kotlin y Java son los lenguajes nativos oficiales para desarrollo Android. Kotlin es ahora el lenguaje preferido por Google, mientras que Java ha sido el lenguaje tradicional desde el inicio de Android.","explicacion_incorrecta":"Python y Ruby no son lenguajes nativos para Android, aunque existen frameworks que permiten usarlos."}}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es una Activity en Android?',
 '["Pantalla", "Componente UI"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Una pantalla/interfaz de usuario"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un servicio en background"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Una Activity representa una pantalla o interfaz de usuario en Android. Es el componente fundamental para la interacción del usuario con la aplicación.","explicacion_incorrecta":"No es una base de datos ni un servicio en background, sino el componente visual principal para interacción con el usuario."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es un Intent en Android?',
 '["Mensajería", "Comunicación entre componentes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un mensaje para comunicar componentes"},
   {"id":"B", "texto":"Una variable"},
   {"id":"C", "texto":"Un tipo de error"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un Intent es un objeto de mensajería que permite la comunicación entre componentes de Android (Activities, Services, BroadcastReceivers), facilitando el paso de datos y acciones.","explicacion_incorrecta":"No es una variable simple ni un tipo de error, sino un mecanismo de mensajería entre componentes de la aplicación."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'Desarrollador Android', 'opcion_multiple',
 '¿Qué es el AndroidManifest.xml?',
 '["Configuración de app", "Permisos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Archivo de configuración de la aplicación"},
   {"id":"B", "texto":"Código fuente principal"},
   {"id":"C", "texto":"Base de datos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El AndroidManifest.xml es el archivo de configuración esencial que declara componentes de la aplicación, permisos, características requeridas y metadatos importantes.","explicacion_incorrecta":"No es el código fuente principal ni una base de datos, sino el archivo descriptor que configura la aplicación Android."}}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'Desarrollador Android', 'abierta',
 'Explica el ciclo de vida de una Activity',
 '["onCreate, onStart, onResume...", "Estados"]'::jsonb,
 '{"min_caracteres": 50, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El ciclo de vida de una Activity incluye los estados onCreate (creación), onStart (visible), onResume (interactiva), onPause (pierde foco), onStop (no visible) y onDestroy (destrucción). Estos callbacks permiten gestionar recursos y estado durante las transiciones de la Activity.","explicacion_incorrecta":"No es un proceso arbitrario, sino una secuencia específica de callbacks que gestiona el estado de la interfaz según la interacción del usuario."},"feedback_generico":"Se espera que menciones los principales métodos del ciclo de vida (onCreate, onStart, onResume, onPause, onStop, onDestroy) y su propósito."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El testing automatizado utiliza scripts y herramientas para ejecutar pruebas de forma automática y repetible sin intervención manual, mejorando la eficiencia y consistencia de las pruebas.","explicacion_incorrecta":"No son pruebas manuales ni diseño de interfaces, sino la automatización del proceso de testing mediante scripts."}}'::jsonb
),
('NV', 'Desarrollo', 'jr', 'QA Automation', 'opcion_multiple',
 '¿Qué es un test case?',
 '["Caso de prueba", "Escenario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Un escenario de prueba con pasos y resultado esperado"},
   {"id":"B", "texto":"Un error en el código"},
   {"id":"C", "texto":"Una función del programa"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un test case es un escenario de prueba que define condiciones, pasos a ejecutar y el resultado esperado para verificar que una funcionalidad trabaja correctamente.","explicacion_incorrecta":"No es un error en el código ni una función del programa, sino un conjunto documentado de pasos para verificar funcionalidad."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'QA Automation', 'opcion_multiple',
 '¿Qué es Selenium?',
 '["Automatización web", "Testing"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Herramienta para automatizar pruebas de aplicaciones web"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un lenguaje de programación"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Selenium es un framework open source para automatizar pruebas de aplicaciones web, permitiendo simular interacciones del usuario en diferentes navegadores.","explicacion_incorrecta":"No es una base de datos ni un lenguaje de programación, sino una herramienta especializada en automatización de pruebas web."}}'::jsonb
),
('NV', 'Desarrollo', 'mid', 'QA Automation', 'abierta',
 'Diferencia entre testing unitario e integración',
 '["Función vs Múltiples componentes", "Aislado vs Conjunto"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El testing unitario prueba componentes individuales de forma aislada (funciones, métodos), mientras que el testing de integración verifica que múltiples componentes funcionen correctamente juntos y se comuniquen adecuadamente.","explicacion_incorrecta":"No son lo mismo; el unitario se enfoca en piezas individuales mientras que integración verifica la interacción entre componentes."},"feedback_generico":"Se espera que menciones que unitario prueba componentes aislados y integración prueba componentes trabajando juntos."}'::jsonb
),
('NV', 'Desarrollo', 'sr', 'QA Automation', 'abierta',
 '¿Qué es el patrón Page Object Model (POM)?',
 '["Patrón de diseño", "Mantenibilidad"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"Page Object Model (POM) es un patrón de diseño que crea objetos que representan páginas web, encapsulando los elementos y acciones de cada página. Esto mejora la mantenibilidad, reutilización y legibilidad de los tests automatizados.","explicacion_incorrecta":"No es simplemente acceder a elementos del DOM, sino crear una capa de abstracción organizada por páginas que facilita el mantenimiento de tests."},"feedback_generico":"Se espera que menciones que POM encapsula elementos y acciones de páginas en objetos para mejorar mantenibilidad."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"SQL (Structured Query Language) es el lenguaje estándar para interactuar con bases de datos relacionales, permitiendo consultar, insertar, actualizar y eliminar datos de manera estructurada.","explicacion_incorrecta":"No es un lenguaje de preguntas simples ni un nivel de calidad, sino el lenguaje estándar para gestionar bases de datos relacionales."}}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es un dashboard?',
 '["Tablero de visualización", "Gráficos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Panel visual que muestra métricas e indicadores clave"},
   {"id":"B", "texto":"Una base de datos"},
   {"id":"C", "texto":"Un tipo de gráfico"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un dashboard es un panel visual interactivo que consolida y muestra métricas e indicadores clave de desempeño (KPIs) en un solo lugar, facilitando el análisis y toma de decisiones.","explicacion_incorrecta":"No es una base de datos ni un tipo de gráfico específico, sino un panel completo que integra múltiples visualizaciones y métricas."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es ETL?',
 '["Extract, Transform, Load", "Proceso de datos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Extract, Transform, Load - proceso de integración de datos"},
   {"id":"B", "texto":"Error Testing Language"},
   {"id":"C", "texto":"External Tool Library"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"ETL (Extract, Transform, Load) es el proceso de extraer datos de diversas fuentes, transformarlos a un formato útil y cargarlos en un sistema de destino, típicamente un data warehouse.","explicacion_incorrecta":"No es un lenguaje de testing ni una librería de herramientas, sino el proceso fundamental de integración y preparación de datos."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Datos', 'abierta',
 'Explica qué es la normalización de datos',
 '["Estructurar datos", "Eliminar redundancia"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"La normalización de datos es el proceso de organizar datos en una base de datos para reducir redundancia y dependencias, dividiendo tablas grandes en tablas más pequeñas y relacionadas para mejorar integridad y eficiencia.","explicacion_incorrecta":"No es simplemente limpiar datos, sino estructurarlos siguiendo reglas específicas (formas normales) para eliminar redundancia y mantener consistencia."},"feedback_generico":"Se espera que menciones la eliminación de redundancia y la estructuración de datos en tablas relacionadas."}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista de Datos', 'opcion_multiple',
 '¿Qué es un Data Warehouse?',
 '["Almacén de datos", "Histórico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema centralizado para almacenar y analizar grandes volúmenes de datos"},
   {"id":"B", "texto":"Una hoja de cálculo"},
   {"id":"C", "texto":"Un tipo de gráfico"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un Data Warehouse es un sistema centralizado que almacena grandes volúmenes de datos históricos de múltiples fuentes, optimizado para análisis y reportería empresarial mediante consultas complejas.","explicacion_incorrecta":"No es una hoja de cálculo ni un gráfico, sino una infraestructura completa diseñada para almacenamiento y análisis masivo de datos empresariales."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un requerimiento funcional describe qué debe hacer el sistema, especificando funcionalidades, comportamientos y operaciones que el sistema debe realizar para satisfacer las necesidades del negocio.","explicacion_incorrecta":"No es hardware ni costos, sino la descripción específica de funcionalidades y comportamientos del sistema."}}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista de Negocios', 'opcion_multiple',
 '¿Qué es un stakeholder?',
 '["Interesado", "Afectado por el proyecto"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Persona u organización con interés en el proyecto"},
   {"id":"B", "texto":"Un tipo de software"},
   {"id":"C", "texto":"Una metodología"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un stakeholder es cualquier persona u organización que tiene interés, es afectada por, o puede influir en el proyecto o sistema, incluyendo usuarios, clientes, patrocinadores y equipos.","explicacion_incorrecta":"No es un software ni una metodología, sino las personas y organizaciones involucradas o afectadas por el proyecto."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Negocios', 'opcion_multiple',
 '¿Qué es un caso de uso?',
 '["Interacción usuario-sistema", "Escenario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Descripción de cómo un usuario interactúa con el sistema"},
   {"id":"B", "texto":"Un error en el software"},
   {"id":"C", "texto":"Una prueba técnica"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un caso de uso describe una secuencia de interacciones entre un actor (usuario) y el sistema para lograr un objetivo específico, documentando el flujo principal y alternativo de acciones.","explicacion_incorrecta":"No es un error ni una prueba técnica, sino una descripción estructurada de cómo los usuarios interactúan con el sistema."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista de Negocios', 'abierta',
 'Diferencia entre requerimiento funcional y no funcional',
 '["Qué hace vs Cómo lo hace", "Funcionalidad vs Calidad"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"Los requerimientos funcionales describen qué debe hacer el sistema (funcionalidades específicas), mientras que los no funcionales describen cómo debe hacerlo (calidad, rendimiento, seguridad, usabilidad). Funcionales son capacidades, no funcionales son restricciones o atributos de calidad.","explicacion_incorrecta":"No son lo mismo; funcionales definen comportamientos del sistema y no funcionales definen características de calidad."},"feedback_generico":"Se espera que menciones que funcionales describen qué hace el sistema y no funcionales cómo lo hace (calidad, rendimiento)."}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista de Negocios', 'abierta',
 '¿Qué es el análisis de brecha (gap analysis)?',
 '["Estado actual vs deseado", "Diferencia"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El análisis de brecha (gap analysis) compara el estado actual de un proceso o sistema con el estado deseado futuro, identificando las diferencias (gaps) y determinando las acciones necesarias para cerrar esas brechas y alcanzar los objetivos.","explicacion_incorrecta":"No es solo identificar problemas, sino comparar sistemáticamente el estado actual con el objetivo y planificar cómo cerrar la brecha."},"feedback_generico":"Se espera que menciones la comparación entre estado actual y deseado, identificando brechas y acciones para cerrarlas."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"QA significa Quality Assurance (Aseguramiento de Calidad), el proceso sistemático de garantizar que productos y servicios cumplan con estándares de calidad establecidos mediante prevención y detección de defectos.","explicacion_incorrecta":"No es acceso rápido ni preguntas y respuestas, sino el proceso de asegurar la calidad del software."}}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista QA', 'opcion_multiple',
 '¿Qué es un bug?',
 '["Error en software", "Defecto"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Error o defecto en el software"},
   {"id":"B", "texto":"Una funcionalidad nueva"},
   {"id":"C", "texto":"Un tipo de virus"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un bug es un error o defecto en el software que causa comportamiento incorrecto, inesperado o no intencionado, impidiendo que el programa funcione como se esperaba.","explicacion_incorrecta":"No es una funcionalidad nueva ni un virus, sino un defecto que impide el funcionamiento correcto del software."}}'::jsonb
),
('NV', 'Analisis TI', 'jr', 'Analista QA', 'opcion_multiple',
 '¿Qué es el testing de regresión?',
 '["Verificar que nada se rompió", "Después de cambios"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Pruebas para verificar que cambios no afectaron funcionalidad existente"},
   {"id":"B", "texto":"Pruebas solo de nuevas funciones"},
   {"id":"C", "texto":"Pruebas de rendimiento"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El testing de regresión verifica que los cambios o nuevas funcionalidades no hayan afectado negativamente las funcionalidades existentes que previamente funcionaban correctamente.","explicacion_incorrecta":"No es solo para nuevas funciones ni para rendimiento, sino para asegurar que los cambios no rompieron funcionalidad previa."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista QA', 'abierta',
 'Explica la diferencia entre verificación y validación',
 '["¿Lo hicimos bien? vs ¿Hicimos lo correcto?", "Proceso vs Producto"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"La verificación pregunta \"¿Estamos construyendo el producto correctamente?\" (cumplimos especificaciones y estándares), mientras que la validación pregunta \"¿Estamos construyendo el producto correcto?\" (satisface necesidades del usuario). Verificación es proceso, validación es producto final.","explicacion_incorrecta":"No son sinónimos; verificación revisa cumplimiento de especificaciones y validación revisa satisfacción de necesidades del usuario."},"feedback_generico":"Se espera que menciones que verificación es cumplir especificaciones (proceso) y validación es cumplir necesidades (producto)."}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista QA', 'opcion_multiple',
 '¿Qué es un plan de pruebas?',
 '["Documento", "Estrategia de testing"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que define estrategia, alcance y recursos de testing"},
   {"id":"B", "texto":"Lista de bugs"},
   {"id":"C", "texto":"Manual de usuario"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un plan de pruebas es un documento formal que define la estrategia, alcance, objetivos, recursos, cronograma y enfoque de las actividades de testing para un proyecto.","explicacion_incorrecta":"No es una simple lista de bugs ni un manual de usuario, sino un documento estratégico que guía todas las actividades de testing."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El Analista Funcional actúa como puente entre el negocio y TI, traduciendo necesidades de negocio en requerimientos funcionales claros y documentados que el equipo técnico pueda implementar.","explicacion_incorrecta":"No programa ni gestiona servidores, sino que analiza necesidades y documenta requerimientos funcionales."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 '¿Qué es un diagrama de flujo?',
 '["Representación visual de proceso", "Pasos"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Representación gráfica de un proceso o algoritmo"},
   {"id":"B", "texto":"Una tabla de datos"},
   {"id":"C", "texto":"Un reporte"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un diagrama de flujo es una representación gráfica que muestra los pasos secuenciales de un proceso o algoritmo usando símbolos estandarizados para facilitar su comprensión y análisis.","explicacion_incorrecta":"No es una tabla de datos ni un reporte, sino una herramienta visual para representar procesos paso a paso."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'opcion_multiple',
 '¿Qué es la especificación funcional?',
 '["Documento detallado", "Cómo debe funcionar"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que describe en detalle cómo debe funcionar el sistema"},
   {"id":"B", "texto":"Manual de usuario"},
   {"id":"C", "texto":"Código fuente"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La especificación funcional es un documento detallado que describe cómo debe funcionar el sistema, incluyendo comportamientos, interfaces, reglas de negocio y flujos de trabajo necesarios para su implementación.","explicacion_incorrecta":"No es un manual de usuario ni código fuente, sino documentación técnica que guía el desarrollo del sistema."}}'::jsonb
),
('NV', 'Analisis TI', 'mid', 'Analista Funcional', 'abierta',
 'Explica qué es el modelado de procesos de negocio',
 '["BPM", "Representar flujos"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El modelado de procesos de negocio (BPM) es la técnica de representar visualmente los flujos de trabajo y procesos organizacionales, identificando actividades, roles, decisiones e interacciones para analizar, optimizar y documentar cómo opera el negocio.","explicacion_incorrecta":"No es solo hacer diagramas, sino analizar y documentar sistemáticamente los procesos completos del negocio con sus actores y flujos."},"feedback_generico":"Se espera que menciones la representación visual de flujos de trabajo y procesos organizacionales para análisis y optimización."}'::jsonb
),
('NV', 'Analisis TI', 'sr', 'Analista Funcional', 'abierta',
 '¿Qué técnicas usarías para elicitar requerimientos?',
 '["Entrevistas, talleres, observación", "Múltiples técnicas"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"Para elicitar requerimientos se usan múltiples técnicas: entrevistas con stakeholders, talleres colaborativos, observación directa de usuarios, análisis de documentación existente, prototipos, encuestas y casos de uso. Cada técnica aporta perspectivas diferentes para capturar necesidades completas.","explicacion_incorrecta":"No es usar una sola técnica, sino combinar múltiples enfoques para obtener requerimientos completos y precisos de diferentes fuentes."},"feedback_generico":"Se espera que menciones al menos 3-4 técnicas como entrevistas, talleres, observación, análisis de documentos o prototipos."}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Microsoft Excel es un programa de hojas de cálculo que permite organizar datos en tablas, realizar cálculos mediante fórmulas, crear gráficos y analizar información de manera eficiente.","explicacion_incorrecta":"No es un editor de imágenes ni una base de datos, sino una aplicación especializada en cálculos y análisis de datos tabulares."}}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Para qué sirve una agenda digital?',
 '["Organizar tareas", "Calendario"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Para organizar eventos, reuniones y tareas"},
   {"id":"B", "texto":"Para editar videos"},
   {"id":"C", "texto":"Para programar"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Una agenda digital sirve para organizar y gestionar eventos, reuniones, tareas y recordatorios de forma electrónica, mejorando la productividad y organización personal o profesional.","explicacion_incorrecta":"No es para editar videos ni para programar, sino para gestionar el tiempo y organizar actividades."}}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es un correo corporativo?',
 '["Email profesional", "Dominio de empresa"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Cuenta de email profesional con dominio de la empresa"},
   {"id":"B", "texto":"Correo personal"},
   {"id":"C", "texto":"Red social"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un correo corporativo es una cuenta de email profesional que utiliza el dominio de la empresa (ej: nombre@empresa.com), proporcionando identidad corporativa y mayor profesionalismo en las comunicaciones.","explicacion_incorrecta":"No es un correo personal ni una red social, sino una herramienta de comunicación profesional con identidad empresarial."}}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es un acta de reunión?',
 '["Documento de registro", "Minuta"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Documento que registra lo tratado en una reunión"},
   {"id":"B", "texto":"Invitación a reunión"},
   {"id":"C", "texto":"Lista de asistentes"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un acta de reunión es un documento formal que registra los temas tratados, decisiones tomadas, acuerdos alcanzados y acciones asignadas durante una reunión, sirviendo como registro oficial.","explicacion_incorrecta":"No es una invitación ni solo una lista de asistentes, sino un registro completo de lo discutido y decidido en la reunión."}}'::jsonb
),
('NV', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
 '¿Qué es la gestión documental?',
 '["Organización de archivos", "Sistema"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema para organizar, almacenar y recuperar documentos"},
   {"id":"B", "texto":"Edición de textos"},
   {"id":"C", "texto":"Impresión de documentos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La gestión documental es un sistema o proceso para organizar, almacenar, gestionar y recuperar documentos de manera eficiente, asegurando su disponibilidad, seguridad y trazabilidad.","explicacion_incorrecta":"No es solo edición o impresión, sino un sistema completo para administrar el ciclo de vida de los documentos organizacionales."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El balance general es un estado financiero que muestra la situación económica de una empresa en un momento específico, presentando sus activos, pasivos y patrimonio, reflejando la ecuación contable fundamental.","explicacion_incorrecta":"No es una lista de empleados ni un presupuesto, sino un reporte financiero que muestra la posición patrimonial de la empresa."}}'::jsonb
),
('NV', 'Administracion', 'jr', 'Analista Contable', 'abierta',
 '¿Qué significa débito y crédito en contabilidad?',
 '["Partida doble", "Cargo y abono"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"En contabilidad, débito (debe o cargo) y crédito (haber o abono) son los dos lados de la partida doble. Débito aumenta activos y gastos, mientras que crédito aumenta pasivos, patrimonio e ingresos. Cada transacción afecta al menos dos cuentas manteniendo el balance.","explicacion_incorrecta":"No son simplemente entrada y salida de dinero, sino conceptos del sistema de partida doble que registran efectos en diferentes tipos de cuentas."},"feedback_generico":"Se espera que menciones la partida doble y cómo débito y crédito afectan diferentes tipos de cuentas."}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué es la conciliación bancaria?',
 '["Comparar registros", "Libro vs Banco"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Proceso de comparar registros contables con extractos bancarios"},
   {"id":"B", "texto":"Transferencia bancaria"},
   {"id":"C", "texto":"Solicitud de préstamo"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La conciliación bancaria es el proceso de comparar y ajustar los registros contables de la empresa con los extractos bancarios para identificar diferencias, verificar saldos y detectar errores o transacciones pendientes.","explicacion_incorrecta":"No es una transferencia ni un préstamo, sino un procedimiento de control para verificar la exactitud de los registros bancarios."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué son las cuentas por pagar?',
 '["Obligaciones", "Deudas"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Deudas u obligaciones que la empresa debe pagar"},
   {"id":"B", "texto":"Dinero que nos deben"},
   {"id":"C", "texto":"Ingresos futuros"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Las cuentas por pagar son obligaciones o deudas que la empresa debe pagar a proveedores, acreedores u otros terceros por bienes o servicios recibidos a crédito, representando un pasivo corriente.","explicacion_incorrecta":"No es dinero que nos deben (eso sería cuentas por cobrar) ni ingresos futuros, sino obligaciones de pago pendientes."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
 '¿Qué es la depreciación?',
 '["Pérdida de valor", "Desgaste"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Pérdida de valor de un activo con el tiempo"},
   {"id":"B", "texto":"Aumento de precio"},
   {"id":"C", "texto":"Tipo de impuesto"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La depreciación es la pérdida de valor de un activo fijo con el tiempo debido a uso, desgaste u obsolescencia, registrándose contablemente como gasto para distribuir el costo del activo a lo largo de su vida útil.","explicacion_incorrecta":"No es un aumento de precio ni un impuesto, sino el reconocimiento contable de la pérdida de valor de activos fijos."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La gestión de recursos humanos es el proceso de administrar el capital humano de la organización, incluyendo reclutamiento, selección, capacitación, desarrollo, evaluación y retención del personal para alcanzar objetivos organizacionales.","explicacion_incorrecta":"No es compra de equipos ni solo gestión financiera, sino la administración integral del personal de la organización."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es un presupuesto?',
 '["Plan financiero", "Ingresos y gastos proyectados"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Plan que estima ingresos y gastos futuros"},
   {"id":"B", "texto":"Informe de ventas"},
   {"id":"C", "texto":"Lista de productos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un presupuesto es un plan financiero que estima ingresos y gastos futuros para un período determinado, permitiendo controlar recursos, tomar decisiones y medir desempeño financiero.","explicacion_incorrecta":"No es un informe de ventas ni una lista de productos, sino una proyección financiera planificada para un período específico."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'abierta',
 'Explica qué es un indicador de gestión (KPI)',
 '["Key Performance Indicator", "Medir desempeño"]'::jsonb,
 '{"min_caracteres": 30, "max_caracteres": 300}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"Un KPI (Key Performance Indicator) es un indicador clave de desempeño que mide cuantitativamente el logro de objetivos estratégicos, permitiendo evaluar el rendimiento de procesos, áreas o proyectos y tomar decisiones basadas en datos.","explicacion_incorrecta":"No es solo un número o métrica cualquiera, sino un indicador específicamente seleccionado que refleja factores críticos de éxito para los objetivos estratégicos."},"feedback_generico":"Se espera que menciones que KPI es un indicador que mide el desempeño en relación a objetivos estratégicos clave."}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es la cadena de suministro?',
 '["Supply Chain", "Proveedores a clientes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Red de proveedores, fabricantes y distribuidores"},
   {"id":"B", "texto":"Lista de empleados"},
   {"id":"C", "texto":"Catálogo de productos"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La cadena de suministro (supply chain) es la red completa de organizaciones, personas, actividades y recursos involucrados en el flujo de productos o servicios desde proveedores hasta clientes finales.","explicacion_incorrecta":"No es una lista de empleados ni un catálogo, sino el sistema completo de flujo de materiales, información y dinero entre proveedores y clientes."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
 '¿Qué es el control interno?',
 '["Procesos de control", "Prevenir fraudes"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Sistema de políticas y procedimientos para proteger activos"},
   {"id":"B", "texto":"Auditoría externa"},
   {"id":"C", "texto":"Seguridad física"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El control interno es un sistema integrado de políticas, procedimientos y prácticas implementados por la organización para proteger activos, asegurar la exactitud de información financiera, promover eficiencia operativa y cumplir con regulaciones.","explicacion_incorrecta":"No es solo auditoría externa ni seguridad física, sino un sistema completo de controles organizacionales internos."}}'::jsonb
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
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La planeación estratégica es el proceso sistemático de definir la visión, misión, objetivos y estrategias organizacionales a largo plazo, determinando cómo la organización alcanzará sus metas y se posicionará en el futuro.","explicacion_incorrecta":"No es un plan de ventas mensual ni tareas diarias, sino el proceso de definir el rumbo estratégico a largo plazo de la organización."}}'::jsonb
),
('NV', 'Administracion', 'mid', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el análisis FODA?',
 '["Fortalezas, Oportunidades, Debilidades, Amenazas", "Diagnóstico estratégico"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Herramienta para analizar fortalezas, oportunidades, debilidades y amenazas"},
   {"id":"B", "texto":"Tipo de presupuesto"},
   {"id":"C", "texto":"Sistema contable"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El análisis FODA es una herramienta estratégica que evalúa Fortalezas y Debilidades internas de la organización, junto con Oportunidades y Amenazas externas del entorno, para formular estrategias competitivas.","explicacion_incorrecta":"No es un presupuesto ni un sistema contable, sino una herramienta de diagnóstico estratégico para evaluar la posición competitiva."}}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 'Explica qué es el balanced scorecard (cuadro de mando integral)',
 '["Perspectivas múltiples", "Indicadores estratégicos"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"El Balanced Scorecard es un sistema de gestión estratégica que evalúa el desempeño organizacional desde cuatro perspectivas: financiera, clientes, procesos internos y aprendizaje/crecimiento. Traduce la estrategia en objetivos e indicadores medibles, permitiendo un seguimiento integral más allá de métricas puramente financieras.","explicacion_incorrecta":"No es solo un tablero de indicadores financieros, sino un marco completo que balancea múltiples perspectivas estratégicas para gestión integral."},"feedback_generico":"Se espera que menciones las cuatro perspectivas y cómo integra indicadores estratégicos más allá de lo financiero."}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'abierta',
 '¿Qué es la gestión del cambio organizacional?',
 '["Change management", "Transición"]'::jsonb,
 '{"min_caracteres": 40, "max_caracteres": 400}'::jsonb,
 '{"tipo_item":"open","nlp":{"explicacion_correcta":"La gestión del cambio organizacional es el proceso estructurado de planificar, implementar y acompañar transformaciones en la organización, abordando aspectos técnicos y humanos para minimizar resistencia, asegurar adopción exitosa y lograr la transición de un estado actual a uno deseado.","explicacion_incorrecta":"No es simplemente anunciar cambios, sino gestionar sistemáticamente la transición considerando personas, procesos y cultura organizacional."},"feedback_generico":"Se espera que menciones el proceso de planificar e implementar transformaciones manejando resistencia y asegurando adopción."}'::jsonb
),
('NV', 'Administracion', 'sr', 'Jefe de Administración', 'opcion_multiple',
 '¿Qué es el ROI (Return on Investment)?',
 '["Retorno de inversión", "Rentabilidad"]'::jsonb,
 '{"opciones": [
   {"id":"A", "texto":"Métrica que mide la rentabilidad de una inversión"},
   {"id":"B", "texto":"Tipo de impuesto"},
   {"id":"C", "texto":"Estado financiero"}
 ], "respuesta_correcta":"A"}'::jsonb,
 '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El ROI (Return on Investment) es una métrica financiera que mide la rentabilidad de una inversión, calculando el beneficio obtenido en relación al costo invertido, expresado típicamente como porcentaje para evaluar eficiencia de inversiones.","explicacion_incorrecta":"No es un impuesto ni un estado financiero, sino un indicador que cuantifica el retorno generado por una inversión."}}'::jsonb
);

-- =============================================================================
-- INSERT PREGUNTAS HABILIDADES BLANDAS TI (4 preguntas - nivel básico)
-- =============================================================================

INSERT INTO pregunta (
  tipo_banco,
  sector,
  nivel,
  meta_cargo,
  tipo_pregunta,
  texto,
  pistas,
  config_respuesta,
  config_evaluacion
) VALUES

-- SOFT SKILLS - Soporte TI
(
  'BL', 'Analista TI', 'jr', 'Soporte TI', 'opcion_multiple',
  'Un usuario muy molesto te llama porque el computador no prende justo antes de una reunión importante. ¿Cuál es la mejor forma de manejar la situación?',
  '["Empatía primero", "Haz preguntas claras sobre lo que ve en pantalla"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Decirle que no puedes ayudar porque tienes muchos tickets"},
    {"id":"B","texto":"Pedirle que lea el manual y volver a llamar si no resulta"},
    {"id":"C","texto":"Escuchar la situación, reconocer la urgencia y guiarlo paso a paso con preguntas simples"},
    {"id":"D","texto":"Derivarlo de inmediato a otra persona sin recopilar información"}
  ],"respuesta_correcta":"C"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La respuesta esperada muestra empatía, reconocimiento de la urgencia y guía paso a paso con preguntas claras.","explicacion_incorrecta":"Respuestas que evitan ayudar, derivan sin contexto o mandan a leer manuales sin guía suelen aumentar la frustración del usuario."}}'::jsonb
),

(
  'BL', 'Analista TI', 'jr', 'Soporte TI', 'abierta',
  'Cuenta una ocasión en la que ayudaste a un usuario no técnico a resolver un problema con su equipo. ¿Qué hiciste y qué resultado tuviste?',
  '["Piensa en alguien real", "Describe qué hiciste tú y cómo terminó la situación"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["situación o contexto del usuario no técnico","acciones que realizaste para ayudar","comunicación simple o lenguaje no técnico","resultado o impacto positivo"]},"feedback_generico":"Se espera un ejemplo concreto donde expliques la situación, qué hiciste tú, cómo lo explicaste y cuál fue el resultado para la persona usuaria."}'::jsonb
),

(
  'BL', 'Analista TI', 'mid', 'Soporte TI', 'opcion_multiple',
  'Tienes un incidente que afecta a toda una gerencia y varios tickets menores, por ejemplo cambio de contraseña. ¿Cómo deberías priorizar?',
  '["Impacto en el negocio", "Comunica tiempos a los demás usuarios"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Atender todos en orden de llegada para ser justo"},
    {"id":"B","texto":"Atender primero los más rápidos para bajar la cola"},
    {"id":"C","texto":"Priorizar el incidente crítico, informar a los demás usuarios sobre la demora y actualizar el estado de sus tickets"},
    {"id":"D","texto":"Cerrar los tickets menores sin avisar para concentrarte en el incidente crítico"}
  ],"respuesta_correcta":"C"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La priorización debe basarse en el impacto al negocio, sin olvidar informar a los demás usuarios sobre tiempos y estado.","explicacion_incorrecta":"Atender solo por orden de llegada o cerrar tickets sin avisar no gestiona bien el impacto ni la comunicación."}}'::jsonb
),

(
  'BL', 'Analista TI', 'sr', 'Soporte TI', 'abierta',
  'Describe una situación en la que lideraste la resolución de un problema crítico que afectaba la continuidad de las operaciones. ¿Cómo coordinaste al equipo y qué aprendieron?',
  '["Piensa en un incidente crítico", "Cuenta qué hizo el equipo y qué hiciste tú"]'::jsonb,
  '{"min_caracteres":120,"max_caracteres":1000,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto del incidente crítico","coordinación del equipo o roles","acciones concretas que se tomaron","comunicación con personas interesadas","lecciones aprendidas o mejoras posteriores"]},"feedback_generico":"Se espera que relates una situación crítica, cómo lideraste al equipo, cómo se coordinó la respuesta y qué aprendizajes obtuvieron para futuras incidencias."}'::jsonb
),

-- SOFT SKILLS - DevOps Engineer
(
  'BL', 'TI', 'jr', 'DevOps Engineer', 'opcion_multiple',
  'Estás automatizando un proceso sencillo y tu script rompe el pipeline de integración continua. ¿Qué deberías hacer?',
  '["Piensa en responsabilidad", "Aprendizaje del error"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Borrar el script y hacer como si nada hubiera pasado"},
    {"id":"B","texto":"Culpar a la herramienta de integración continua por ser poco estable"},
    {"id":"C","texto":"Comunicar el problema, revertir el cambio, analizar la causa y proponer una corrección"},
    {"id":"D","texto":"Esperar a que alguien más lo arregle"}
  ],"respuesta_correcta":"C"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La respuesta esperada incluye comunicación, reversión del cambio, análisis de causa raíz y propuesta de corrección.","explicacion_incorrecta":"Ocultar el problema o culpar a la herramienta dificulta el aprendizaje y afecta la confianza del equipo."}}'::jsonb
),

(
  'BL', 'TI', 'jr', 'DevOps Engineer', 'abierta',
  'Cuenta una experiencia en la que automatizaste una tarea manual, aunque fuera pequeña. ¿Qué problema resolviste y qué impacto tuvo en el equipo?',
  '["Piensa en algo real", "Explica qué cambió después de automatizar"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["tarea manual inicial","acción de automatización o herramienta usada","impacto en tiempo o errores","beneficio para el equipo o proceso"]},"feedback_generico":"Se espera un ejemplo de cómo pasaste de una tarea manual a una automatizada y el efecto en eficiencia o calidad para el equipo."}'::jsonb
),

(
  'BL', 'TI', 'mid', 'DevOps Engineer', 'opcion_multiple',
  'El equipo de desarrollo quiere hacer un cambio urgente en producción sin usar el pipeline de integración y entrega continua porque dicen que no hay tiempo. ¿Cuál es la mejor respuesta?',
  '["Riesgo frente a velocidad", "Negocia sin ceder la calidad"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Aceptar y hacer el cambio manual sin registrar nada"},
    {"id":"B","texto":"Negarte sin explicar los motivos"},
    {"id":"C","texto":"Explicar los riesgos, buscar una alternativa rápida dentro del pipeline y dejar registro de la decisión tomada"},
    {"id":"D","texto":"Decir que lo hagan ellos y no involucrarte"}
  ],"respuesta_correcta":"C"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera que expliques los riesgos, busques una alternativa rápida dentro del pipeline y mantengas trazabilidad de la decisión.","explicacion_incorrecta":"Cambios manuales sin registro o sin explicar riesgos comprometen la estabilidad y la gobernanza."}}'::jsonb
),

(
  'BL', 'TI', 'sr', 'DevOps Engineer', 'abierta',
  'Describe una situación en la que lideraste una mejora en la plataforma, por ejemplo monitoreo, alertas o infraestructura como código, que redujo incidentes o tareas manuales. ¿Qué hiciste y qué resultados obtuviste?',
  '["Piensa en una mejora real", "Cuenta antes y después del cambio"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["problema o dolor inicial como incidentes o tareas manuales","acción de mejora como monitoreo, alertas o infraestructura como código","resultado medible como menos incidentes o menos tareas manuales","colaboración con otros equipos si aplica"]},"feedback_generico":"Se espera que relates una iniciativa concreta de mejora de plataforma y cómo impactó en estabilidad o carga operacional."}'::jsonb
),

-- SOFT SKILLS - SysAdmin
(
  'BL', 'TI', 'jr', 'SysAdmin', 'opcion_multiple',
  'Un usuario interno reporta que el sistema anda lento, pero no entrega detalles. ¿Cómo deberías responder?',
  '["Haz preguntas concretas", "Mantén buena actitud con el cliente interno"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Decirle que seguramente es su computador y cerrar el ticket"},
    {"id":"B","texto":"Pedirle con calma más detalles, por ejemplo qué sistema, desde cuándo y qué ve en pantalla, y registrar la información en el ticket"},
    {"id":"C","texto":"Pedirle que mande un correo a otro equipo"},
    {"id":"D","texto":"Ignorar el ticket hasta que se vuelva crítico"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera una respuesta con actitud de servicio, preguntas concretas y registro adecuado del incidente.","explicacion_incorrecta":"Ignorar, derivar sin información o culpar al usuario deteriora la relación y dificulta el diagnóstico."}}'::jsonb
),

(
  'BL', 'TI', 'jr', 'SysAdmin', 'abierta',
  'Cuenta una ocasión en la que registraste y seguiste un incidente hasta su cierre. ¿Cómo te aseguraste de dejar buena documentación para el equipo?',
  '["Piensa en un incidente real", "Menciona registro, seguimiento y cierre"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["registro inicial del incidente","actualización de estado o comunicaciones","documentación de causa y solución","uso posterior de la documentación como lecciones o base de conocimiento"]},"feedback_generico":"Se espera que describas cómo registraste, diste seguimiento y documentaste un incidente hasta su cierre."}'::jsonb
),

(
  'BL', 'TI', 'mid', 'SysAdmin', 'opcion_multiple',
  'Se genera una mesa de incidentes por caída de un servicio crítico. ¿Cuál es tu mejor aporte como administrador de sistemas?',
  '["Coordina con datos concretos", "Comunica avances"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Trabajar en silencio sin decir nada hasta tener la solución final"},
    {"id":"B","texto":"Compartir métricas y registros relevantes, proponer hipótesis y comunicar claramente las acciones que estás realizando"},
    {"id":"C","texto":"Esperar a que otro equipo resuelva porque es más rápido"},
    {"id":"D","texto":"Buscar culpables en lugar de soluciones"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"En una mesa de incidentes se espera aportar datos, hipótesis y comunicación clara de acciones.","explicacion_incorrecta":"Trabajar aislado, no comunicar o enfocarse en culpables no ayuda a resolver ni a coordinar."}}'::jsonb
),

(
  'BL', 'TI', 'sr', 'SysAdmin', 'abierta',
  'Describe una situación en la que tuviste que mantener la continuidad operativa de una infraestructura crítica, por ejemplo durante un cambio, corte o falla. ¿Cómo organizaste al equipo y qué resultados lograste?',
  '["Piensa en continuidad operativa", "Incluye decisiones que tomaste tú"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto de criticidad o riesgo","planificación o acciones de mitigación","coordinación de equipo o turnos","resultado en términos de continuidad o minimización de impacto"]},"feedback_generico":"Se espera un ejemplo de cómo organizaste al equipo y las acciones que permitieron mantener o recuperar la continuidad operativa."}'::jsonb
),

-- SOFT SKILLS - Desarrollador Backend
(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Backend', 'opcion_multiple',
  'Estás trabajando remoto y detectas que tu implementación impactará a otro servicio de backend. ¿Qué haces?',
  '["Comunica antes de romper cosas", "Trabajo en equipo"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Hacer el cambio sin avisar y ver qué pasa"},
    {"id":"B","texto":"Avisar al otro desarrollador, coordinar el cambio y acordar pruebas de integración"},
    {"id":"C","texto":"Esperar a que el otro equipo encuentre el problema"},
    {"id":"D","texto":"Cancelar el cambio sin informar"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La coordinación previa y las pruebas de integración son clave para evitar interrupciones entre servicios.","explicacion_incorrecta":"Cambiar sin avisar o esperar a que otros detecten el problema genera incidentes evitables."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Backend', 'abierta',
  'Cuenta una vez en la que pediste ayuda para resolver un error complejo en backend. ¿Cómo lo abordaste y qué aprendiste?',
  '["Piensa en un error real", "Incluye qué cambiaste después de esa experiencia"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["explicación del error o síntoma","cómo pediste ayuda o colaboraste","pasos para encontrar la causa","aprendizajes y cambios posteriores"]},"feedback_generico":"Se busca un ejemplo donde se vea colaboración, apertura a pedir ayuda y aprendizaje técnico o de procesos."}'::jsonb
),

(
  'BL', 'Desarrollador', 'mid', 'Desarrollador Backend', 'opcion_multiple',
  'Calidad asegura que existe un error crítico en una interfaz de programación que tú desarrollaste, cerca de una entrega. ¿Cuál es tu mejor reacción?',
  '["Calidad y colaboración", "No se trata de culpar"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Decir que en tu máquina funciona y cerrar el error"},
    {"id":"B","texto":"Revisar el caso con calidad, reproducir el problema, analizar la causa y proponer una solución con su impacto"},
    {"id":"C","texto":"Ignorar el error porque llega tarde"},
    {"id":"D","texto":"Pedir que negocio lo acepte tal cual sin informar el riesgo"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera colaboración con calidad, análisis de causa y propuesta de solución con evaluación de impacto.","explicacion_incorrecta":"Negar el problema o ignorarlo perjudica la calidad y la relación con calidad y con negocio."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'sr', 'Desarrollador Backend', 'abierta',
  'Describe una experiencia en la que lideraste la mejora de la calidad del backend, por ejemplo pruebas, revisión de código o refactorización. ¿Qué problema resolviste y qué impacto tuvo en el equipo?',
  '["Piensa en una mejora concreta", "Cuenta antes y después"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["problema de calidad inicial como errores o deuda técnica","acciones de mejora como pruebas automáticas, revisión de código o refactorización","impacto medible o percibido como menos errores o mejor mantenibilidad","impacto en la colaboración del equipo"]},"feedback_generico":"Se espera un caso en el que hayas impulsado mejoras de calidad y el impacto en estabilidad o flujo de trabajo."}'::jsonb
),

-- SOFT SKILLS - Desarrollador Frontend
(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Frontend', 'opcion_multiple',
  'El equipo de diseño te entrega una maqueta que en dispositivos móviles se ve poco usable. ¿Qué haces?',
  '["Trabajo con diseño", "No cambies todo solo"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Implementar igual la maqueta aunque sepas que será incómoda"},
    {"id":"B","texto":"Modificar todo por tu cuenta sin avisar a diseño"},
    {"id":"C","texto":"Pedir una reunión breve, mostrar ejemplos del problema en móvil y proponer ajustes a la maqueta"},
    {"id":"D","texto":"Rechazar la maqueta sin dar detalles"}
  ],"respuesta_correcta":"C"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera colaboración con diseño, aportando evidencia y propuestas, en vez de cambiar todo solo o implementar algo poco usable.","explicacion_incorrecta":"Actuar en solitario o sin retroalimentación clara dificulta la relación con diseño y la experiencia de usuario."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Frontend', 'abierta',
  'Cuenta una situación en la que tuviste que ajustar una interfaz según comentarios de usuarios o diseño. ¿Qué cambiaste y qué resultado obtuviste?',
  '["Piensa en retroalimentación real", "Describe el cambio y su efecto"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["comentarios de usuarios o diseño","ajustes realizados en la interfaz","impacto en usabilidad o satisfacción"]},"feedback_generico":"Se espera un ejemplo de cómo incorporaste retroalimentación para mejorar la interfaz y qué efecto tuvo."}'::jsonb
),

(
  'BL', 'Desarrollador', 'mid', 'Desarrollador Frontend', 'opcion_multiple',
  'Trabajas con un desarrollador de backend y surgen problemas por mal entendimiento de los contratos de la interfaz de programación. ¿Qué acción es más efectiva?',
  '["Comunicación y acuerdos claros"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Seguir asumiendo cómo funciona la interfaz y corregir sobre la marcha"},
    {"id":"B","texto":"Definir en conjunto el contrato de solicitud y respuesta, documentarlo y adaptar el código de ambos lados"},
    {"id":"C","texto":"Pedir que el backend se adapte solo a lo que tú necesitas"},
    {"id":"D","texto":"Dejar de hablar con el otro desarrollador"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La definición y documentación compartida del contrato reduce malentendidos y retrabajo.","explicacion_incorrecta":"Asumir comportamientos o imponer cambios sin acuerdo aumenta errores de integración."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'sr', 'Desarrollador Frontend', 'abierta',
  'Describe una vez en la que lideraste la mejora de la experiencia de usuario en un producto o módulo. ¿Qué problema detectaste y cómo se vio el impacto en los usuarios?',
  '["Piensa en una mejora de experiencia de usuario", "Menciona datos o señales del impacto si puedes"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["problema de experiencia de usuario detectado","cambios aplicados en la interfaz o flujo","impacto medido o percibido como menos errores o mejor conversión","mejor retroalimentación de usuarios o negocio"]},"feedback_generico":"Se espera que describas una mejora de experiencia de usuario concreta y cómo se reflejó en el comportamiento o la percepción de las personas usuarias."}'::jsonb
),

-- SOFT SKILLS - Desarrollador Fullstack
(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Fullstack', 'opcion_multiple',
  'En un sprint te asignan tareas de frontend y backend. ¿Cómo organizas tu trabajo?',
  '["Piensa en dependencias y comunicación"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Hacer un poco de cada cosa sin terminar nada"},
    {"id":"B","texto":"Revisar dependencias, acordar prioridades con el equipo y avanzar en bloques terminando tareas completas"},
    {"id":"C","texto":"Hacer solo las tareas que más te gustan"},
    {"id":"D","texto":"Esperar a que la persona que lidera el marco ágil te diga exactamente qué hacer"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera organización por dependencias y prioridades de equipo, cerrando tareas de forma completa.","explicacion_incorrecta":"Ir saltando de tarea en tarea sin terminar o elegir solo lo que gusta afecta el avance del sprint."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'jr', 'Desarrollador Fullstack', 'abierta',
  'Cuenta una experiencia en la que tuviste que aprender algo nuevo, por ejemplo una tecnología de frontend o backend, para sacar adelante una tarea. ¿Cómo lo hiciste?',
  '["Piensa en un aprendizaje concreto", "Explica cómo te organizaste para aprender"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto o necesidad de aprender algo nuevo","estrategia de aprendizaje como tutoriales, documentación o mentores","aplicación del aprendizaje en la tarea","resultado o impacto en el trabajo"]},"feedback_generico":"Se busca ver cómo abordas el aprendizaje autónomo ante un reto técnico concreto."}'::jsonb
),

(
  'BL', 'Desarrollador', 'mid', 'Desarrollador Fullstack', 'opcion_multiple',
  'Estás en medio de un desarrollo y negocio cambia prioridades del sprint. ¿Qué haces?',
  '["Piensa en adaptación y comunicación con el equipo"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Ignorar el cambio y terminar lo que estabas haciendo"},
    {"id":"B","texto":"Revisar con el equipo el impacto del cambio, reordenar el trabajo y comunicar qué quedará dentro o fuera del sprint"},
    {"id":"C","texto":"Aceptar el cambio pero sin modificar el plan"},
    {"id":"D","texto":"Decir que el cambio es imposible sin analizarlo"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La respuesta esperada coordina con el equipo, replanifica y comunica el alcance revisado.","explicacion_incorrecta":"Ignorar o aceptar cambios sin replanificar genera sobrecarga y expectativas poco realistas."}}'::jsonb
),

(
  'BL', 'Desarrollador', 'sr', 'Desarrollador Fullstack', 'abierta',
  'Describe un caso en el que ayudaste al equipo a mejorar la colaboración entre frontend, backend y personas de operaciones de plataforma. ¿Qué hiciste para alinear a todos?',
  '["Piensa en un caso real", "Incluye reuniones, acuerdos o cambios de proceso que impulsaste"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["problema de comunicación o coordinación inicial","acciones concretas para alinear como reuniones, acuerdos o documentación","mejoras en flujo de trabajo o tiempos","impacto percibido por el equipo"]},"feedback_generico":"Se busca un ejemplo de liderazgo transversal mejorando la colaboración entre roles técnicos."}'::jsonb
),

-- SOFT SKILLS - Analista de Datos
(
  'BL', 'TI', 'jr', 'Analista de Datos', 'opcion_multiple',
  'Te piden un informe para hoy pero no está claro qué decisión se tomará con esos datos. ¿Qué haces?',
  '["Piensa en entender el objetivo", "No es solo hacer gráficos"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Generar muchos gráficos y esperar que alguno sirva"},
    {"id":"B","texto":"Hacer algunas preguntas breves para entender qué decisión quieren tomar y enfocar el análisis en eso"},
    {"id":"C","texto":"Negarte a hacer el informe"},
    {"id":"D","texto":"Enviar solo la tabla de datos sin comentarios"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera que primero entiendas la decisión o propósito para orientar el análisis.","explicacion_incorrecta":"Generar gráficos sin foco o entregar datos sin contexto limita el valor del análisis."}}'::jsonb
),

(
  'BL', 'TI', 'jr', 'Analista de Datos', 'abierta',
  'Cuenta una ocasión en la que detectaste un problema en la calidad de los datos, por ejemplo duplicados o inconsistencias. ¿Cómo lo manejaste?',
  '["Piensa en un caso real", "Incluye a quién avisaste y qué se hizo"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["tipo de problema de calidad de datos","acciones para validarlo o cuantificarlo","comunicación a dueños de datos o personas interesadas","acciones para corregir o prevenir"]},"feedback_generico":"Se espera un ejemplo de gestión de calidad de datos, desde la detección hasta la comunicación y corrección."}'::jsonb
),

(
  'BL', 'TI', 'mid', 'Analista de Datos', 'opcion_multiple',
  'Detectas inconsistencias importantes en las fuentes de datos de un panel de control clave. ¿Cuál es la mejor acción?',
  '["Calidad de datos primero", "Comunica el riesgo"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Ignorarlas porque el panel ya está en producción"},
    {"id":"B","texto":"Documentar las inconsistencias, informar a los dueños de datos y proponer pasos para corregirlas"},
    {"id":"C","texto":"Eliminar los datos problemáticos sin avisar"},
    {"id":"D","texto":"Cambiar las métricas para que no se note"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La calidad de datos es prioritaria, por lo que documentar, informar y proponer correcciones es la respuesta esperada.","explicacion_incorrecta":"Ignorar, ocultar o alterar datos sin transparencia puede generar decisiones equivocadas."}}'::jsonb
),

(
  'BL', 'TI', 'sr', 'Analista de Datos', 'abierta',
  'Describe una experiencia en la que un análisis tuyo generó un impacto importante, por ejemplo cambio de estrategia o mejora de un proceso. ¿Qué descubriste y qué se hizo con esa información?',
  '["Piensa en un caso con impacto", "Cuenta qué decisión cambió gracias al análisis"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["hallazgo relevante del análisis","decisión o cambio que se tomó","impacto en negocio o proceso","cómo lo presentaste a la gerencia o equipo"]},"feedback_generico":"Se espera que muestres cómo un análisis influyó en decisiones importantes y cómo lo comunicaste."}'::jsonb
),

-- SOFT SKILLS - Analista de Negocios
(
  'BL', 'Administracion', 'jr', 'Analista de Negocios', 'opcion_multiple',
  'Durante una reunión, distintas áreas usan nombres distintos para el mismo indicador. ¿Qué haces?',
  '["Piensa en claridad y acuerdos", "Glosario común"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Anotar todo tal cual y dejar que cada área use su nombre"},
    {"id":"B","texto":"Definir en conjunto un nombre y descripción, documentarlo y validarlo con todos"},
    {"id":"C","texto":"Elegir tú un nombre sin consultar"},
    {"id":"D","texto":"Suspender la reunión y no retomar el tema"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera alinear lenguaje y definiciones mediante acuerdos y documentación compartida.","explicacion_incorrecta":"Dejar múltiples nombres sin consenso o imponer uno sin consulta genera confusión."}}'::jsonb
),

(
  'BL', 'Administracion', 'jr', 'Analista de Negocios', 'abierta',
  'Cuenta una ocasión en la que ayudaste a un área a entender mejor sus indicadores o reportes. ¿Qué hiciste para explicarlos?',
  '["Piensa en una explicación que diste", "Incluye cómo adaptaste el lenguaje"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["adaptar lenguaje a audiencia no técnica","usar ejemplos o visualizaciones","aclarar cómo se calcula el indicador","reacción o comprensión lograda"]},"feedback_generico":"Se busca ver cómo facilitas la comprensión de indicadores a personas no expertas."}'::jsonb
),

(
  'BL', 'Administracion', 'mid', 'Analista de Negocios', 'opcion_multiple',
  'Distintas áreas como ventas, operaciones y finanzas tienen prioridades distintas para un mismo proyecto. ¿Cuál es tu mejor rol?',
  '["Gestión de personas interesadas", "Buscar alineamiento"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Apoyar solo a la que tenga más poder"},
    {"id":"B","texto":"Facilitar una conversación para alinear objetivos, definir criterios en común y documentar acuerdos"},
    {"id":"C","texto":"Hacer un informe distinto para cada área sin buscar un mínimo común"},
    {"id":"D","texto":"No involucrarte en el conflicto"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"El rol esperado es facilitar alineamiento entre personas interesadas con criterios y acuerdos compartidos.","explicacion_incorrecta":"Tomar partido o fragmentar soluciones sin alineamiento aumenta el conflicto."}}'::jsonb
),

(
  'BL', 'Administracion', 'sr', 'Analista de Negocios', 'abierta',
  'Describe una experiencia en la que tu análisis ayudó a la gerencia a tomar una decisión crítica, por ejemplo cambio de producto, inversión o reducción de costos. ¿Cómo lo presentaste?',
  '["Piensa en una decisión importante", "Incluye cómo comunicaste los hallazgos"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto de la decisión crítica","insumos del análisis","forma de presentación como resumen ejecutivo o visualizaciones","decisión tomada gracias al análisis"]},"feedback_generico":"Se espera un ejemplo donde se vea la conexión entre tu análisis y una decisión de alto impacto."}'::jsonb
),

-- SOFT SKILLS - Analista QA
(
  'BL', 'TI', 'jr', 'Analista QA', 'opcion_multiple',
  'En una reunión diaria, desarrollo y negocio no se ponen de acuerdo sobre la prioridad de un defecto. ¿Qué puedes aportar como aseguramiento de calidad?',
  '["Piensa en riesgo y evidencias"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"No decir nada para no entrar en conflicto"},
    {"id":"B","texto":"Aportar datos sobre el impacto del defecto, ejemplos de uso y ayudar a estimar el riesgo para decidir su prioridad"},
    {"id":"C","texto":"Decir que todos los defectos son críticos siempre"},
    {"id":"D","texto":"Apoyar automáticamente al que hable más fuerte"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Calidad aporta evidencia, contexto de uso y análisis de riesgo para priorizar defectos.","explicacion_incorrecta":"No opinar o etiquetar todo como crítico sin criterio no ayuda a priorizar."}}'::jsonb
),

(
  'BL', 'TI', 'jr', 'Analista QA', 'abierta',
  'Cuenta una ocasión en la que detectaste un problema importante antes de que llegara a producción. ¿Cómo lo comunicaste al equipo?',
  '["Piensa en un error real o un riesgo", "Incluye la reacción del equipo"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["tipo de problema detectado","momento del ciclo en que se detectó","forma de comunicarlo como datos, ejemplos y severidad","reacción del equipo o cambio que se hizo"]},"feedback_generico":"Se espera ver cómo tu intervención como calidad evitó un problema en producción y cómo lo comunicaste."}'::jsonb
),

(
  'BL', 'TI', 'mid', 'Analista QA', 'opcion_multiple',
  'Ves que el mismo tipo de defecto se repite en varios lanzamientos. ¿Qué deberías impulsar?',
  '["Mejora continua", "No solo reportar otra vez"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Seguir reportando el mismo defecto cada vez"},
    {"id":"B","texto":"Proponer un análisis de causa raíz y ajustar pruebas, criterios de aceptación o proceso"},
    {"id":"C","texto":"Dejar de reportarlo porque es repetitivo"},
    {"id":"D","texto":"Pedir más tiempo sin cambiar nada del proceso"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera impulsar mejoras de proceso mediante análisis de causa raíz y ajustes.","explicacion_incorrecta":"Repetir el reporte sin atacar la causa no reduce la recurrencia del defecto."}}'::jsonb
),

(
  'BL', 'TI', 'sr', 'Analista QA', 'abierta',
  'Describe una experiencia en la que ayudaste a mejorar la cultura de calidad en tu equipo o empresa. ¿Qué hiciste diferente?',
  '["Piensa en cambios de prácticas, reuniones o métricas", "Cuenta el impacto en el equipo"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["prácticas o rituales nuevos como pruebas en pareja o refinamiento de criterios","cambios en la forma de trabajar o medir calidad","impacto en defectos, colaboración o percepción de calidad"]},"feedback_generico":"Se espera un ejemplo de cómo impulsaste prácticas o cambios que elevaron la cultura de calidad."}'::jsonb
),

-- SOFT SKILLS - Analista Funcional
(
  'BL', 'TI', 'jr', 'Analista Funcional', 'opcion_multiple',
  'Durante el levantamiento de requerimientos, los usuarios usan distintos términos para lo mismo. ¿Qué haces?',
  '["Piensa en claridad de lenguaje", "Glosario compartido"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Anotar todo tal cual y dejar que desarrollo interprete"},
    {"id":"B","texto":"Crear y validar con ellos un glosario común con términos y definiciones claras"},
    {"id":"C","texto":"Elegir tú los nombres sin consultar"},
    {"id":"D","texto":"Terminar la reunión y no retomar el tema"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Un glosario validado ayuda a evitar malentendidos entre usuarios y equipo técnico.","explicacion_incorrecta":"Dejar términos variados sin alinear complica el desarrollo y las pruebas."}}'::jsonb
),

(
  'BL', 'TI', 'jr', 'Analista Funcional', 'abierta',
  'Cuenta una situación en la que tuviste que explicar un proceso o requisito complejo a alguien no técnico. ¿Cómo lo hiciste?',
  '["Piensa en un caso real", "Incluye ejemplos o apoyos visuales si los usaste"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["uso de lenguaje simple o metáforas","uso de diagramas o ejemplos","verificación de entendimiento","resultado en la comprensión de la persona"]},"feedback_generico":"Se busca ver tu capacidad de traducir complejidad en explicaciones claras para personas no técnicas."}'::jsonb
),

(
  'BL', 'TI', 'mid', 'Analista Funcional', 'opcion_multiple',
  'En un proyecto con alta presión, te piden recortar documentación de análisis. ¿Qué propones?',
  '["Documentación mínima pero útil"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Eliminar toda la documentación para ganar tiempo"},
    {"id":"B","texto":"Acordar con el equipo un conjunto mínimo de flujos críticos, reglas clave y criterios de aceptación y mantener al menos eso"},
    {"id":"C","texto":"Negarte a avanzar sin documentar todo en detalle"},
    {"id":"D","texto":"Documentar solo en tus notas personales"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Documentación mínima pero alineada que incluya flujos críticos, reglas y criterios equilibra tiempo y claridad.","explicacion_incorrecta":"Documentar nada o solo para uso personal dificulta la colaboración y el mantenimiento."}}'::jsonb
),

(
  'BL', 'TI', 'sr', 'Analista Funcional', 'abierta',
  'Describe una experiencia en la que ayudaste a alinear a negocio, desarrollo y aseguramiento de calidad en torno al alcance de un proyecto complejo. ¿Cómo evitaste el crecimiento descontrolado del alcance?',
  '["Piensa en un proyecto real", "Incluye acuerdos y mecanismos de control que usaste"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["definición clara de alcance","criterios de aceptación compartidos","proceso de control de cambios","mecanismos de comunicación con personas interesadas"]},"feedback_generico":"Se espera un caso donde se vea cómo alineaste a las partes y controlaste el crecimiento del alcance con acuerdos y procesos claros."}'::jsonb
),

-- SOFT SKILLS - Asistente Administrativo
(
  'BL', 'Administracion', 'jr', 'Asistente Administrativo', 'opcion_multiple',
  'Tu jefe te pide un informe para ahora ya, pero ya tienes otras tareas comprometidas para el día. ¿Qué haces?',
  '["Piensa en gestión del tiempo y comunicación"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Intentar hacerlo todo sin avisar si algo no se cumple"},
    {"id":"B","texto":"Explicar tu carga actual, pedir priorizar tareas y reorganizar tu día en base a eso"},
    {"id":"C","texto":"Decir que no harás el informe porque estás ocupado"},
    {"id":"D","texto":"Ignorar las otras tareas y hacer solo el informe"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La gestión esperada implica comunicar la carga actual y acordar prioridades con la jefatura.","explicacion_incorrecta":"Intentar hacerlo todo sin avisar o ignorar tareas suele llevar a incumplimientos inesperados."}}'::jsonb
),

(
  'BL', 'Administracion', 'jr', 'Asistente Administrativo', 'abierta',
  'Cuenta una ocasión en la que debiste organizar muchas tareas al mismo tiempo en la oficina. ¿Cómo decidiste por dónde empezar?',
  '["Piensa en un día ajetreado", "Incluye cómo priorizaste"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["lista o visión de todas las tareas","criterios de prioridad como urgencia, importancia y dependencia","planificación del día","resultado en cumplimiento de tareas"]},"feedback_generico":"Se espera un ejemplo de cómo organizas y priorizas en contextos de alta carga de trabajo."}'::jsonb
),

(
  'BL', 'Administracion', 'mid', 'Asistente Administrativo', 'opcion_multiple',
  'Notas un error en un documento que ya fue enviado a un cliente. ¿Cuál es la mejor acción?',
  '["Piensa en responsabilidad y relación con el cliente"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"No decir nada para evitar problemas"},
    {"id":"B","texto":"Informar a tu jefe, proponer corregir el documento y enviar una versión actualizada si es necesario"},
    {"id":"C","texto":"Echarle la culpa a otra persona"},
    {"id":"D","texto":"Eliminar el documento del archivo y olvidarlo"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Reconocer el error, informar y corregir mantiene la confianza con el cliente.","explicacion_incorrecta":"Ocultar o culpar a otros deteriora la relación y la ética profesional."}}'::jsonb
),

(
  'BL', 'Administracion', 'sr', 'Asistente Administrativo', 'abierta',
  'Describe una experiencia en la que apoyaste a tu equipo o jefatura en un periodo de alta carga de trabajo, por ejemplo cierre de mes o evento importante. ¿Qué hiciste para que todo saliera adelante?',
  '["Piensa en un periodo de alta presión", "Incluye cómo ayudaste a organizar al equipo"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto de alta carga como cierre o evento","acciones de organización o apoyo","coordinación con el equipo o jefatura","resultado final o aprendizaje"]},"feedback_generico":"Se busca ver cómo te comportas en situaciones de alta presión y cómo ayudas a tu equipo a salir adelante."}'::jsonb
),

-- SOFT SKILLS - Analista Contable
(
  'BL', 'Administracion', 'jr', 'Analista Contable', 'opcion_multiple',
  'Durante el registro de facturas encuentras un monto que no cuadra con el documento enviado. ¿Qué haces?',
  '["Piensa en exactitud y comunicación"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Ajustar el monto para que cuadre y seguir"},
    {"id":"B","texto":"Revisar el documento, consultar la diferencia con quien corresponda y registrar correctamente el valor"},
    {"id":"C","texto":"Ignorar el problema porque el monto es pequeño"},
    {"id":"D","texto":"Registrar cualquier valor y corregir después si alguien reclama"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera exactitud y validación de montos incluso si la diferencia parece pequeña.","explicacion_incorrecta":"Ajustar sin preguntar o ignorar diferencias compromete la confiabilidad de los estados."}}'::jsonb
),

(
  'BL', 'Administracion', 'jr', 'Analista Contable', 'abierta',
  'Cuenta una situación en la que detectaste un error contable o administrativo. ¿Cómo lo corregiste?',
  '["Piensa en un error real", "Incluye qué hiciste para evitar que volviera a ocurrir"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["tipo de error detectado","acción para corregir el registro","comunicación a responsables si aplica","medida preventiva para evitar recurrencia"]},"feedback_generico":"Se espera un ejemplo donde se vea responsabilidad, corrección y propuesta de mejora del proceso."}'::jsonb
),

(
  'BL', 'Administracion', 'mid', 'Analista Contable', 'opcion_multiple',
  'Durante el cierre contable descubres una diferencia que no puedes explicar rápidamente. El plazo para entregar los estados es corto. ¿Qué haces?',
  '["Piensa en ética y tiempos"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Ajustar la cifra para que cuadre sin investigar"},
    {"id":"B","texto":"Informar la diferencia, investigar lo posible y acordar un plan para terminar el análisis si no alcanzas"},
    {"id":"C","texto":"Retrasar la entrega sin informar a nadie"},
    {"id":"D","texto":"Eliminar la cuenta con diferencia del estado financiero"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"La transparencia y un plan de análisis complementario equilibran ética y tiempos de entrega.","explicacion_incorrecta":"Ajustar cifras o eliminar cuentas sin explicación compromete la integridad de los estados."}}'::jsonb
),

(
  'BL', 'Administracion', 'sr', 'Analista Contable', 'abierta',
  'Describe una experiencia en la que tuviste que explicar información contable compleja a alguien sin conocimientos financieros, por ejemplo un gerente o cliente. ¿Cómo lo hiciste comprensible?',
  '["Piensa en una explicación importante", "Incluye ejemplos o metáforas si las usaste"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["tema contable complejo","adaptación del lenguaje con metáforas o ejemplos","foco en lo que la persona necesitaba decidir","resultado en la comprensión o decisión del interlocutor"]},"feedback_generico":"Se busca ver tu capacidad de traducir conceptos contables complejos a un lenguaje accesible."}'::jsonb
),

-- SOFT SKILLS - Encargado de Administración
(
  'BL', 'Administracion', 'jr', 'Encargado de Administración', 'opcion_multiple',
  'Debes mantener orden físico y digital de documentación legal y laboral. Notas que varios documentos no están actualizados. ¿Qué haces?',
  '["Piensa en orden y proactividad"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Dejar los documentos como están para no generar trabajo extra"},
    {"id":"B","texto":"Hacer un inventario, priorizar qué actualizar y proponer un plan para regularizar la documentación"},
    {"id":"C","texto":"Eliminar los documentos antiguos sin revisar su importancia"},
    {"id":"D","texto":"Esperar a que el directorio pida algo específico para recién ordenar"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera proactividad, diagnóstico e implementación de un plan de actualización.","explicacion_incorrecta":"Ignorar o eliminar documentos sin análisis puede generar riesgos legales o administrativos."}}'::jsonb
),

(
  'BL', 'Administracion', 'jr', 'Encargado de Administración', 'abierta',
  'Cuenta una ocasión en la que organizaste o mejoraste el orden de documentos o procesos administrativos en tu trabajo o estudios. ¿Qué cambió con tu mejora?',
  '["Piensa en un cambio concreto", "Incluye antes y después"]'::jsonb,
  '{"min_caracteres":80,"max_caracteres":800,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["situación inicial de desorden","acción de orden o mejora implementada","nueva forma de trabajo o acceso a la información","beneficio percibido como menos tiempo o menos errores"]},"feedback_generico":"Se espera un ejemplo concreto donde tu intervención haya mejorado el orden o la eficiencia administrativa."}'::jsonb
),

(
  'BL', 'Administracion', 'mid', 'Encargado de Administración', 'opcion_multiple',
  'Debes informar al directorio sobre una desviación importante en el presupuesto. ¿Qué es lo más adecuado?',
  '["Transparencia con propuesta de acción"]'::jsonb,
  '{"opciones":[
    {"id":"A","texto":"Ocultar la desviación para evitar preguntas difíciles"},
    {"id":"B","texto":"Presentar la desviación con datos claros, explicar las causas y proponer acciones para corregirla"},
    {"id":"C","texto":"Mencionar solo los resultados positivos y omitir los negativos"},
    {"id":"D","texto":"Culpar a otra área sin mostrar información"}
  ],"respuesta_correcta":"B"}'::jsonb,
  '{"tipo_item":"choice","nlp":{"explicacion_correcta":"Se espera transparencia, explicación de causas y propuesta de medidas correctivas.","explicacion_incorrecta":"Ocultar desviaciones o culpar sin datos erosiona la confianza del directorio."}}'::jsonb
),

(
  'BL', 'Administracion', 'sr', 'Encargado de Administración', 'abierta',
  'Describe una experiencia en la que tuviste que liderar al equipo administrativo en un periodo de alta presión, por ejemplo auditoría, cierre de año o cambio importante. ¿Cómo lo manejaste?',
  '["Piensa en un momento crítico", "Cuenta cómo apoyaste al equipo y qué resultados obtuvieron"]'::jsonb,
  '{"min_caracteres":150,"max_caracteres":1200,"formato":"STAR"}'::jsonb,
  '{"tipo_item":"open","star":{"sugerido":true},"nlp":{"frases_clave_esperadas":["contexto de alta presión como auditoría, cierre o cambio","acciones de coordinación, apoyo o priorización","comunicación con el equipo y otras áreas","resultado final y aprendizajes"]},"feedback_generico":"Se busca ver tu rol de liderazgo administrativo en momentos críticos y cómo ayudaste al equipo."}'::jsonb
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
