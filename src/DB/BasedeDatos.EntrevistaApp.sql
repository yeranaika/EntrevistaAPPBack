-- Encabezado recomendado
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS app;
SET search_path TO app, public;

BEGIN;

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

CREATE INDEX idx_consentimiento_texto_vigente
    ON consentimiento_texto (vigente);

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
    usuario_id UUID PRIMARY KEY
        REFERENCES usuario(usuario_id) ON DELETE CASCADE,
    dias_semana VARCHAR(50) NOT NULL,      -- Ej: 'LUNES,MARTES,VIERNES'
    hora        VARCHAR(5)  NOT NULL,      -- Formato 'HH:MM'
    tipo_practica VARCHAR(32) NOT NULL,    -- Ej: 'TEST', 'ENTREVISTA', 'REPASO'
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

-- === TABLA MODIFICADA ===
CREATE TABLE pregunta (
    pregunta_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_banco       VARCHAR(5),
    sector           VARCHAR(80),
    nivel            VARCHAR(3),
    texto            TEXT NOT NULL,
    pistas           JSONB,        -- Cambiado a JSONB para optimización
    config_respuesta JSONB,        -- NUEVA COLUMNA: Contiene opciones o reglas
    activa           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- ========================

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
-- =============================================================================
-- NIVEL JUNIOR (10 Preguntas) - Fundamentos, Sintaxis y Lógica Básica
-- =============================================================================

-- 1. HTML Semántico
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    '¿Cuál es el propósito principal de usar etiquetas semánticas en HTML5 (como <header>, <article>, <footer>)?',
    '["Mejora la accesibilidad (screen readers)", "Ayuda al SEO"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Hacer que la web se vea más bonita"}, {"id": "B", "texto": "Mejorar la accesibilidad y el SEO"}, {"id": "C", "texto": "Ejecutar scripts más rápido"}], "respuesta_correcta": "B" }'::jsonb
);

-- 2. CSS Selectores
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    'En CSS, ¿cómo seleccionas un elemento que tiene el id "menu"?',
    '["El punto (.) es para clases", "La almohadilla (#) es para IDs"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": ".menu"}, {"id": "B", "texto": "#menu"}, {"id": "C", "texto": "menu"}, {"id": "D", "texto": "*menu"}], "respuesta_correcta": "B" }'::jsonb
);

-- 3. Git Básico
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    'Si quieres ver el historial de commits en tu repositorio, ¿qué comando usas?',
    '["Muestra una lista cronológica", "Log significa registro"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "git status"}, {"id": "B", "texto": "git log"}, {"id": "C", "texto": "git history"}, {"id": "D", "texto": "git commit"}], "respuesta_correcta": "B" }'::jsonb
);

-- 4. JS Arrays
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    '¿Qué método de Array en JavaScript crea un *nuevo* array con los resultados de la llamada a una función indicada aplicados a cada uno de sus elementos?',
    '["ForEach no retorna nada", "Map transforma"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "forEach()"}, {"id": "B", "texto": "map()"}, {"id": "C", "texto": "filter()"}, {"id": "D", "texto": "reduce()"}], "respuesta_correcta": "B" }'::jsonb
);

-- 5. SQL Básico
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    'Escribe una consulta SQL para seleccionar todos los usuarios de la tabla "usuarios" que sean mayores de 18 años.',
    '["Usa la cláusula WHERE", "El operador es >"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 15, "max_caracteres": 100, "tips_evaluador": "SELECT * FROM usuarios WHERE edad > 18" }'::jsonb
);

-- 6. Concepto API
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    '¿Qué significan las siglas API?',
    '["Application...", "Interfaz de programación"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Application Programming Interface"}, {"id": "B", "texto": "Advanced Programming Interaction"}, {"id": "C", "texto": "Automated Protocol Interface"}], "respuesta_correcta": "A" }'::jsonb
);

-- 7. POO Básico
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    'En Programación Orientada a Objetos, ¿qué es una Clase?',
    '["Es como un plano o molde", "Define atributos y métodos"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 300, "placeholder": "Es una plantilla para crear objetos..." }'::jsonb
);

-- 8. HTTP Status
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    '¿Qué código de estado HTTP indica que la solicitud fue exitosa?',
    '["Es el código estándar de OK", "Está en el rango de los 200"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "400"}, {"id": "B", "texto": "500"}, {"id": "C", "texto": "200"}, {"id": "D", "texto": "301"}], "respuesta_correcta": "C" }'::jsonb
);

-- 9. Tipos de datos
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    '¿Cuál de los siguientes NO es un tipo de dato primitivo en JavaScript?',
    '["Los objetos son tipos de referencia", "String y Boolean son primitivos"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "String"}, {"id": "B", "texto": "Boolean"}, {"id": "C", "texto": "Object"}, {"id": "D", "texto": "Undefined"}], "respuesta_correcta": "C" }'::jsonb
);

-- 10. Lógica condicional
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'jr',
    'Explica qué hace la sentencia "else if" en una estructura de control.',
    '["Se ejecuta si el primer IF falla", "Permite múltiples condiciones encadenadas"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 20, "max_caracteres": 200 }'::jsonb
);


-- =============================================================================
-- NIVEL mid (10 Preguntas) - Patrones, Ciclo de vida, DB y Herramientas
-- =============================================================================

-- 11. Principios SOLID
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    'En los principios SOLID, ¿qué significa la "S" (Single Responsibility Principle)?',
    '["Una clase debe tener una única razón para cambiar", "No mezclar lógica de negocio con presentación, por ejemplo"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 500 }'::jsonb
);

-- 12. REST vs SOAP
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Cuál es una ventaja principal de REST sobre SOAP?',
    '["SOAP usa XML estricto", "REST permite JSON y es más ligero"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "REST es más seguro por defecto"}, {"id": "B", "texto": "REST usa menos ancho de banda y es más flexible (JSON)"}, {"id": "C", "texto": "REST solo funciona en Windows"}], "respuesta_correcta": "B" }'::jsonb
);

-- 13. Índices en Base de Datos
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Cuándo NO deberías poner un índice en una columna de base de datos?',
    '["Tablas muy pequeñas", "Columnas con valores booleanos (poca cardinalidad)"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Cuando la tabla tiene millones de registros"}, {"id": "B", "texto": "Cuando la columna tiene baja cardinalidad (ej: Sexo M/F) y muchas escrituras"}, {"id": "C", "texto": "Cuando se usa mucho en el WHERE"}], "respuesta_correcta": "B" }'::jsonb
);

-- 14. Git Avanzado
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    'Explica la diferencia entre "git merge" y "git rebase".',
    '["Merge crea un commit de unión", "Rebase reescribe la historia linealmente"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600, "tips_evaluador": "Merge preserva historia, Rebase la limpia pero es peligroso en ramas compartidas." }'::jsonb
);

-- 15. Inyección de Dependencias
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Qué problema resuelve la Inyección de Dependencias?',
    '["Desacoplamiento", "Facilita el testing"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Mejora la velocidad de compilación"}, {"id": "B", "texto": "Reduce el acoplamiento entre clases y facilita el testing"}, {"id": "C", "texto": "Encripta el código fuente"}], "respuesta_correcta": "B" }'::jsonb
);

-- 16. Autenticación JWT
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    'En un token JWT, ¿dónde se encuentra la información de los "claims" (datos del usuario)?',
    '["El token tiene 3 partes: Header, Payload, Signature", "Es la parte del medio"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Header"}, {"id": "B", "texto": "Payload"}, {"id": "C", "texto": "Signature"}], "respuesta_correcta": "B" }'::jsonb
);

-- 17. Pruebas Unitarias
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Qué es un "Mock" en el contexto de Unit Testing?',
    '["Simulación", "Evita llamar a la base de datos real"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 30, "max_caracteres": 400 }'::jsonb
);

-- 18. Complejidad Algorítmica
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    'Si tienes dos bucles anidados recorriendo el mismo array de tamaño N, ¿cuál es la complejidad Big O?',
    '["N veces N", "Cuadrática"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "O(n)"}, {"id": "B", "texto": "O(log n)"}, {"id": "C", "texto": "O(n^2)"}, {"id": "D", "texto": "O(1)"}], "respuesta_correcta": "C" }'::jsonb
);

-- 19. Contenedores
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Para qué sirve el archivo "docker-compose.yml"?',
    '["Orquestación local", "Levantar múltiples servicios a la vez"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 40, "max_caracteres": 400 }'::jsonb
);

-- 20. HTTP Métodos
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'mid',
    '¿Qué significa que un método HTTP sea "idempotente"?',
    '["Si lo ejecutas 1000 veces, el resultado en el servidor es el mismo que si lo ejecutas 1 vez", "Ejemplo: DELETE o PUT"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Que es muy rápido"}, {"id": "B", "texto": "Que múltiples peticiones idénticas tienen el mismo efecto que una sola"}, {"id": "C", "texto": "Que siempre retorna error"}], "respuesta_correcta": "B" }'::jsonb
);


-- =============================================================================
-- NIVEL SENIOR (10 Preguntas) - Arquitectura, Escalabilidad, Liderazgo
-- =============================================================================

-- 21. Microservicios vs Monolito
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    '¿Cuál es el mayor desafío operativo al migrar de un Monolito a Microservicios?',
    '["No es el código, es la red y la observabilidad", "Trazabilidad distribuida"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Escribir más código"}, {"id": "B", "texto": "Complejidad en despliegue, monitoreo y consistencia de datos"}, {"id": "C", "texto": "El costo de las licencias"}], "respuesta_correcta": "B" }'::jsonb
);

-- 22. Teorema CAP
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'En el diseño de sistemas distribuidos, si eliges Disponibilidad (A) y Tolerancia a Particiones (P), ¿qué estás sacrificando según el Teorema CAP?',
    '["No puedes tener las 3 letras a la vez", "Los datos pueden no estar actualizados al instante"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Seguridad"}, {"id": "B", "texto": "Consistencia (Consistency)"}, {"id": "C", "texto": "Velocidad"}], "respuesta_correcta": "B" }'::jsonb
);

-- 23. Escalamiento de Base de Datos
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'Explica qué es el "Database Sharding" y cuándo lo recomendarías.',
    '["Particionamiento horizontal", "Cuando una sola instancia ya no soporta la carga de escritura"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 60, "max_caracteres": 800 }'::jsonb
);

-- 24. Caching Strategies
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    '¿En qué consiste la estrategia de caché "Write-Through"?',
    '["Escritura síncrona", "Escribe en caché y en DB al mismo tiempo"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Escribe solo en caché y luego asíncronamente en DB"}, {"id": "B", "texto": "Escribe en caché y DB simultáneamente antes de confirmar"}, {"id": "C", "texto": "Lee de caché y si falla lee de DB"}], "respuesta_correcta": "B" }'::jsonb
);

-- 25. Deuda Técnica
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'Como líder técnico, ¿cómo convences a los stakeholders de negocio para dedicar tiempo a pagar deuda técnica?',
    '["Habla en términos de riesgo y velocidad futura", "No uses jerga técnica"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 100, "max_caracteres": 1500, "tips_evaluador": "Debe mencionar el riesgo de frenar features futuros o inestabilidad." }'::jsonb
);

-- 26. Seguridad Web Avanzada
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'Para almacenar contraseñas de usuarios, ¿cuál es la práctica estándar actual?',
    '["No basta con MD5 o SHA1 simples", "Necesita Salt y coste computacional"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Encriptación reversible (AES)"}, {"id": "B", "texto": "Hashing con algoritmos lentos y Salt (ej: Bcrypt, Argon2)"}, {"id": "C", "texto": "Texto plano en base de datos segura"}], "respuesta_correcta": "B" }'::jsonb
);

-- 27. CI/CD
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'Describe qué es un "Canary Deployment".',
    '["Piensa en el canario en la mina de carbón", "Probar con un pequeño % de usuarios reales"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600 }'::jsonb
);

-- 28. Load Balancers
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    '¿Cuál es la diferencia entre un balanceador de carga de Capa 4 (L4) y uno de Capa 7 (L7)?',
    '["Modelo OSI", "Transporte (IP/Puerto) vs Aplicación (HTTP/URL)"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "L4 entiende URLs y Cookies, L7 solo IPs"}, {"id": "B", "texto": "L4 balancea por IP/Puerto (TCP/UDP), L7 balancea por contenido (HTTP)"}, {"id": "C", "texto": "L7 es hardware y L4 es software"}], "respuesta_correcta": "B" }'::jsonb
);

-- 29. Observabilidad
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    'En un sistema distribuido, ¿para qué sirve el "Distributed Tracing" (Trazabilidad Distribuida)?',
    '["Seguir una petición a través de varios microservicios", "Identificar cuellos de botella"]'::jsonb,
    '{ "tipo": "abierta_texto", "min_caracteres": 50, "max_caracteres": 600 }'::jsonb
);

-- 30. Cloud Native
INSERT INTO pregunta (tipo_banco, sector, nivel, texto, pistas, config_respuesta) VALUES (
    'TECH', 'Desarrollo', 'sr',
    '¿Qué característica define mejor a una aplicación "Cloud Native"?',
    '["Contenedores, microservicios, DevOps", "No es solo estar en la nube, es cómo se construye"]'::jsonb,
    '{ "tipo": "seleccion_unica", "opciones": [{"id": "A", "texto": "Que está hospedada en AWS"}, {"id": "B", "texto": "Que usa máquinas virtuales"}, {"id": "C", "texto": "Diseñada para ser escalable, resiliente y observable (12-Factor App)"}], "respuesta_correcta": "C" }'::jsonb
);

COMMIT;