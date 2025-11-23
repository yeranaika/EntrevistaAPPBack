-- ========================================
-- INSERTAR PREGUNTAS DE PRUEBA
-- Para probar el sistema de nivelación por cargo
-- ========================================

-- Verificar si ya existen preguntas
SELECT COUNT(*) as preguntas_existentes 
FROM app.pregunta 
WHERE tipo_banco = 'NV';

-- ========================================
-- PREGUNTAS PARA: Desarrollador Full Stack
-- ========================================

-- Nivel Junior (básico)
INSERT INTO app.pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, config_respuesta, pistas, activa, fecha_creacion)
VALUES 
('NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'opcion_multiple', 
 '¿Qué es HTML?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un lenguaje de programación"},{"id":"B","texto":"Un lenguaje de marcado"},{"id":"C","texto":"Una base de datos"},{"id":"D","texto":"Un framework"}],"respuesta_correcta":"B"}',
 'HTML significa HyperText Markup Language y se usa para estructurar contenido web.',
 true, NOW()),

('NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué significa CSS?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Computer Style Sheets"},{"id":"B","texto":"Cascading Style Sheets"},{"id":"C","texto":"Creative Style System"},{"id":"D","texto":"Code Style Syntax"}],"respuesta_correcta":"B"}',
 'CSS se usa para dar estilo y diseño a páginas web.',
 true, NOW()),

('NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es JavaScript?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un lenguaje de programación del lado del cliente"},{"id":"B","texto":"Una base de datos"},{"id":"C","texto":"Un servidor web"},{"id":"D","texto":"Un sistema operativo"}],"respuesta_correcta":"A"}',
 'JavaScript es el lenguaje de programación que hace las páginas web interactivas.',
 true, NOW()),

('NV', 'Desarrollo', 'jr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es una variable en programación?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un espacio en memoria para almacenar datos"},{"id":"B","texto":"Una función"},{"id":"C","texto":"Un archivo"},{"id":"D","texto":"Un servidor"}],"respuesta_correcta":"A"}',
 'Las variables son contenedores para almacenar valores de datos.',
 true, NOW());

-- Nivel Mid (intermedio)
INSERT INTO app.pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, config_respuesta, pistas, activa, fecha_creacion)
VALUES 
('NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es REST?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un protocolo de red"},{"id":"B","texto":"Un estilo arquitectónico para APIs"},{"id":"C","texto":"Un lenguaje de programación"},{"id":"D","texto":"Una base de datos"}],"respuesta_correcta":"B"}',
 'REST (Representational State Transfer) es un estilo arquitectónico para diseñar APIs web.',
 true, NOW()),

('NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es un ORM?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Object-Relational Mapping"},{"id":"B","texto":"Online Resource Manager"},{"id":"C","texto":"Operational Risk Management"},{"id":"D","texto":"Open Resource Model"}],"respuesta_correcta":"A"}',
 'Un ORM mapea objetos de programación a tablas de bases de datos relacionales.',
 true, NOW()),

('NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es JWT?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Java Web Token"},{"id":"B","texto":"JSON Web Token"},{"id":"C","texto":"JavaScript Web Tool"},{"id":"D","texto":"Joint Web Technology"}],"respuesta_correcta":"B"}',
 'JWT es un estándar para crear tokens de acceso que permiten la autenticación.',
 true, NOW()),

('NV', 'Desarrollo', 'mid', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es Docker?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Una plataforma de contenedores"},{"id":"B","texto":"Un lenguaje de programación"},{"id":"C","texto":"Una base de datos"},{"id":"D","texto":"Un framework web"}],"respuesta_correcta":"A"}',
 'Docker permite empaquetar aplicaciones en contenedores portables.',
 true, NOW());

-- Nivel Senior (avanzado)
INSERT INTO app.pregunta (tipo_banco, sector, nivel, meta_cargo, tipo_pregunta, texto, config_respuesta, pistas, activa, fecha_creacion)
VALUES 
('NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué patrón arquitectónico separa la lógica de negocio de la presentación?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"MVC (Model-View-Controller)"},{"id":"B","texto":"Singleton"},{"id":"C","texto":"Factory"},{"id":"D","texto":"Observer"}],"respuesta_correcta":"A"}',
 'MVC es un patrón que separa la aplicación en tres componentes principales.',
 true, NOW()),

('NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es SOLID en programación orientada a objetos?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un conjunto de principios de diseño"},{"id":"B","texto":"Un framework"},{"id":"C","texto":"Un lenguaje de programación"},{"id":"D","texto":"Una base de datos"}],"respuesta_correcta":"A"}',
 'SOLID son 5 principios para escribir código mantenible y escalable.',
 true, NOW()),

('NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es el patrón Repository?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Abstrae el acceso a datos"},{"id":"B","texto":"Gestiona la UI"},{"id":"C","texto":"Maneja autenticación"},{"id":"D","texto":"Controla el routing"}],"respuesta_correcta":"A"}',
 'El patrón Repository encapsula la lógica de acceso a datos.',
 true, NOW()),

('NV', 'Desarrollo', 'sr', 'Desarrollador Full Stack', 'opcion_multiple',
 '¿Qué es CI/CD?',
 '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Continuous Integration/Continuous Deployment"},{"id":"B","texto":"Code Integration/Code Deployment"},{"id":"C","texto":"Container Integration/Container Distribution"},{"id":"D","texto":"Client Interface/Client Design"}],"respuesta_correcta":"A"}',
 'CI/CD automatiza la integración y despliegue de código.',
 true, NOW());

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
