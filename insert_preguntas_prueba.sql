-- ========================================
-- PASO 1: Agregar columnas faltantes a la tabla pregunta
-- ========================================
ALTER TABLE app.pregunta 
ADD COLUMN IF NOT EXISTS tipo_pregunta VARCHAR(20) DEFAULT 'seleccion_unica',
ADD COLUMN IF NOT EXISTS meta_cargo VARCHAR(120);

-- ========================================
-- PASO 2: Limpiar preguntas de prueba anteriores (opcional)
-- ========================================
-- DELETE FROM app.pregunta WHERE tipo_banco = 'NV' AND meta_cargo = 'Desarrollador Full Stack';

-- ========================================
-- PASO 3: INSERTAR PREGUNTAS DE PRUEBA
-- Para probar el sistema de nivelación por cargo
-- ========================================

-- ========================================
-- PREGUNTAS PARA: Desarrollador Full Stack
-- ========================================

INSERT INTO app.pregunta (pregunta_id, tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, pistas, config_respuesta, activa, fecha_creacion)
VALUES
-- Nivel Junior (básico)
(gen_random_uuid(), 'NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es HTML?', '["Lenguaje de marcado", "Estructura web"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un lenguaje de programación"},{"id":"B", "texto":"Un lenguaje de marcado"},{"id":"C", "texto":"Una base de datos"},{"id":"D", "texto":"Un framework"}], "respuesta_correcta":"B"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué significa CSS?', '["Hojas de estilo", "Diseño web"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Computer Style Sheets"},{"id":"B", "texto":"Cascading Style Sheets"},{"id":"C", "texto":"Creative Style System"},{"id":"D", "texto":"Code Style Syntax"}], "respuesta_correcta":"B"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es JavaScript?', '["Lenguaje del navegador", "Interactividad web"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un lenguaje de programación del lado del cliente"},{"id":"B", "texto":"Una base de datos"},{"id":"C", "texto":"Un servidor web"},{"id":"D", "texto":"Un sistema operativo"}], "respuesta_correcta":"A"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es una variable en programación?', '["Almacenamiento de datos", "Contenedor de valores"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un espacio en memoria para almacenar datos"},{"id":"B", "texto":"Una función"},{"id":"C", "texto":"Un archivo"},{"id":"D", "texto":"Un servidor"}], "respuesta_correcta":"A"}'::jsonb, true, now()),

-- Nivel Mid (intermedio)
(gen_random_uuid(), 'NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es REST?', '["Arquitectura de APIs", "Transferencia de estado"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un protocolo de red"},{"id":"B", "texto":"Un estilo arquitectónico para APIs"},{"id":"C", "texto":"Un lenguaje de programación"},{"id":"D", "texto":"Una base de datos"}], "respuesta_correcta":"B"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es un ORM?', '["Mapeo objeto-relacional", "Abstracción de BD"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Object-Relational Mapping"},{"id":"B", "texto":"Online Resource Manager"},{"id":"C", "texto":"Operational Risk Management"},{"id":"D", "texto":"Open Resource Model"}], "respuesta_correcta":"A"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es JWT?', '["Token de autenticación", "JSON Web Token"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Java Web Token"},{"id":"B", "texto":"JSON Web Token"},{"id":"C", "texto":"JavaScript Web Tool"},{"id":"D", "texto":"Joint Web Technology"}], "respuesta_correcta":"B"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es Docker?', '["Contenedores", "Virtualización ligera"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Una plataforma de contenedores"},{"id":"B", "texto":"Un lenguaje de programación"},{"id":"C", "texto":"Una base de datos"},{"id":"D", "texto":"Un framework web"}], "respuesta_correcta":"A"}'::jsonb, true, now()),

-- Nivel Senior (avanzado)
(gen_random_uuid(), 'NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué patrón arquitectónico separa la lógica de negocio de la presentación?', '["MVC", "Separación de capas"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"MVC (Model-View-Controller)"},{"id":"B", "texto":"Singleton"},{"id":"C", "texto":"Factory"},{"id":"D", "texto":"Observer"}], "respuesta_correcta":"A"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es SOLID en programación orientada a objetos?', '["Principios de diseño", "5 principios OOP"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Un conjunto de principios de diseño"},{"id":"B", "texto":"Un framework"},{"id":"C", "texto":"Un lenguaje de programación"},{"id":"D", "texto":"Una base de datos"}], "respuesta_correcta":"A"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es el patrón Repository?', '["Abstracción de datos", "Capa de acceso a datos"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Abstrae el acceso a datos"},{"id":"B", "texto":"Gestiona la UI"},{"id":"C", "texto":"Maneja autenticación"},{"id":"D", "texto":"Controla el routing"}], "respuesta_correcta":"A"}'::jsonb, true, now()),
(gen_random_uuid(), 'NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'seleccion_unica', '¿Qué es CI/CD?', '["Integración continua", "Despliegue automatizado"]'::jsonb, '{"tipo": "seleccion_unica", "opciones": [{"id":"A", "texto":"Continuous Integration/Continuous Deployment"},{"id":"B", "texto":"Code Integration/Code Deployment"},{"id":"C", "texto":"Container Integration/Container Distribution"},{"id":"D", "texto":"Client Interface/Client Design"}], "respuesta_correcta":"A"}'::jsonb, true, now());

-- Verificar preguntas insertadas
SELECT 
    meta_cargo,
    nivel,
    COUNT(*) as total_preguntas
FROM app.pregunta
WHERE tipo_banco = 'NV' 
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY nivel;

-- Verificar total
SELECT COUNT(*) as total_preguntas_disponibles
FROM app.pregunta
WHERE tipo_banco = 'NV'
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true;


-- ========================================
-- PASO 4: Verificar preguntas insertadas
-- ========================================
SELECT 
    meta_cargo,
    nivel,
    COUNT(*) as total_preguntas
FROM app.pregunta
WHERE tipo_banco = 'NV' 
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY nivel;

-- Verificar total
SELECT COUNT(*) as total_preguntas_disponibles
FROM app.pregunta
WHERE tipo_banco = 'NV'
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true;


-- ========================================
-- PASO 5: CORREGIR tipo_pregunta
-- ========================================
-- El código busca 'opcion_multiple' pero insertamos 'seleccion_unica'
UPDATE app.pregunta 
SET tipo_pregunta = 'opcion_multiple'
WHERE tipo_banco = 'NV' 
  AND meta_cargo = 'Desarrollador Full Stack'
  AND tipo_pregunta = 'seleccion_unica';

-- Verificar corrección
SELECT tipo_pregunta, COUNT(*) 
FROM app.pregunta 
WHERE tipo_banco = 'NV' AND meta_cargo = 'Desarrollador Full Stack'
GROUP BY tipo_pregunta;
