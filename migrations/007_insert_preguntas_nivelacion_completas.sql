-- =============================================================================
-- Migración 007: Banco de Preguntas de Nivelación Completo
-- =============================================================================
-- Inserta 120 preguntas de opción múltiple distribuidas en 4 áreas principales
-- con 3 niveles de dificultad cada una (básico=1, intermedio=2, avanzado=3)
-- =============================================================================

BEGIN;

-- Verificar que la tabla pregunta_nivelacion existe
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                   WHERE table_schema = 'app'
                   AND table_name = 'pregunta_nivelacion') THEN
        RAISE EXCEPTION 'La tabla app.pregunta_nivelacion no existe. Ejecuta primero la migración 006.';
    END IF;
END $$;

-- =============================================================================
-- ÁREA 1: DESARROLLO (30 preguntas)
-- =============================================================================

-- DESARROLLO - Nivel Básico (1) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Desarrollo', 1, '¿Qué es una variable en programación?', '["Espacio de memoria para almacenar datos","Una función predefinida","Un tipo de bucle"]', 0, 'Una variable es un espacio de memoria nombrado que almacena un valor que puede cambiar durante la ejecución del programa'),
('Desarrollo', 1, '¿Cuál es el propósito de un bucle for?', '["Repetir un bloque de código un número específico de veces","Tomar decisiones","Declarar variables"]', 0, 'El bucle for se utiliza para iterar un número específico de veces sobre una secuencia'),
('Desarrollo', 1, '¿Qué es HTML?', '["Lenguaje de marcado para crear páginas web","Lenguaje de programación","Base de datos"]', 0, 'HTML (HyperText Markup Language) es el lenguaje de marcado estándar para crear páginas web'),
('Desarrollo', 1, '¿Qué significa CSS?', '["Cascading Style Sheets","Computer Style System","Code Style Sheet"]', 0, 'CSS significa Cascading Style Sheets y se usa para dar estilo a páginas HTML'),
('Desarrollo', 1, '¿Qué es un array/arreglo?', '["Colección ordenada de elementos","Un tipo de función","Un operador matemático"]', 0, 'Un array es una estructura de datos que almacena una colección ordenada de elementos'),
('Desarrollo', 1, '¿Qué es Git?', '["Sistema de control de versiones","Editor de código","Lenguaje de programación"]', 0, 'Git es un sistema de control de versiones distribuido para rastrear cambios en el código'),
('Desarrollo', 1, '¿Qué es una función?', '["Bloque de código reutilizable que realiza una tarea específica","Una variable especial","Un tipo de dato"]', 0, 'Una función es un bloque de código que puede ser llamado múltiples veces para realizar una tarea'),
('Desarrollo', 1, '¿Qué es JSON?', '["Formato de intercambio de datos basado en texto","Lenguaje de programación","Framework de desarrollo"]', 0, 'JSON (JavaScript Object Notation) es un formato ligero para intercambiar datos'),
('Desarrollo', 1, '¿Qué es un comentario en código?', '["Texto que el compilador/intérprete ignora, usado para documentar","Código que se ejecuta primero","Una función especial"]', 0, 'Los comentarios son anotaciones en el código fuente que son ignoradas al ejecutar el programa'),
('Desarrollo', 1, '¿Qué es debugging?', '["Proceso de encontrar y corregir errores en el código","Escribir código nuevo","Optimizar el rendimiento"]', 0, 'Debugging es el proceso de identificar, analizar y eliminar errores (bugs) del código');

-- DESARROLLO - Nivel Intermedio (2) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Desarrollo', 2, '¿Qué es REST?', '["Protocolo de comunicación","Estilo arquitectónico para APIs web","Framework JavaScript"]', 1, 'REST (Representational State Transfer) es un estilo arquitectónico para diseñar APIs basadas en HTTP'),
('Desarrollo', 2, '¿Qué es un ORM?', '["Object-Relational Mapping - mapea objetos a tablas de BD","Lenguaje de consultas","Sistema operativo"]', 0, 'Un ORM es una técnica que permite interactuar con bases de datos usando objetos en lugar de SQL'),
('Desarrollo', 2, '¿Qué es el patrón MVC?', '["Model-View-Controller, separa lógica de negocio, presentación y control","Lenguaje de programación","Servidor web"]', 0, 'MVC es un patrón de diseño que separa la aplicación en tres componentes: Modelo, Vista y Controlador'),
('Desarrollo', 2, '¿Qué es async/await?', '["Sintaxis para manejar operaciones asíncronas","Tipo de bucle","Estructura de datos"]', 0, 'async/await es sintaxis moderna para trabajar con promesas y código asíncrono de forma más legible'),
('Desarrollo', 2, '¿Qué es un API Gateway?', '["Punto de entrada único para múltiples microservicios","Base de datos","Editor de texto"]', 0, 'Un API Gateway actúa como punto de entrada unificado que enruta peticiones a los microservicios apropiados'),
('Desarrollo', 2, '¿Qué es dependency injection?', '["Patrón donde las dependencias se proporcionan externamente","Instalación de paquetes","Compilación de código"]', 0, 'Inyección de dependencias es un patrón donde los objetos reciben sus dependencias desde el exterior'),
('Desarrollo', 2, '¿Qué es CI/CD?', '["Continuous Integration/Continuous Deployment","Lenguaje de programación","Framework web"]', 0, 'CI/CD automatiza la integración y despliegue continuo de código'),
('Desarrollo', 2, '¿Qué es GraphQL?', '["Lenguaje de consulta para APIs","Base de datos","Sistema operativo"]', 0, 'GraphQL es un lenguaje de consulta y runtime para APIs que permite solicitar exactamente los datos necesarios'),
('Desarrollo', 2, '¿Qué es Docker?', '["Plataforma de contenedores para empaquetar aplicaciones","Editor de código","Lenguaje de programación"]', 0, 'Docker permite empaquetar aplicaciones con sus dependencias en contenedores portables'),
('Desarrollo', 2, '¿Qué es middleware?', '["Software que actúa como intermediario entre aplicaciones","Base de datos","Framework"]', 0, 'Middleware es software que se sitúa entre aplicaciones para facilitar comunicación y gestión de datos');

-- DESARROLLO - Nivel Avanzado (3) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Desarrollo', 3, '¿Qué son los principios SOLID?', '["Guía de sintaxis","5 principios de diseño orientado a objetos","Framework"]', 1, 'SOLID son 5 principios (Single Responsibility, Open/Closed, Liskov Substitution, Interface Segregation, Dependency Inversion) para diseño de software mantenible'),
('Desarrollo', 3, '¿Qué es Event Sourcing?', '["Patrón donde los cambios se almacenan como secuencia de eventos","Base de datos NoSQL","Lenguaje de programación"]', 0, 'Event Sourcing almacena el estado como una secuencia de eventos en lugar de solo el estado actual'),
('Desarrollo', 3, '¿Qué es CQRS?', '["Command Query Responsibility Segregation - separa lecturas y escrituras","Framework JavaScript","Servidor web"]', 0, 'CQRS separa las operaciones de lectura y escritura en modelos diferentes para optimizar cada una'),
('Desarrollo', 3, '¿Qué es el patrón Saga?', '["Patrón para transacciones distribuidas en microservicios","Tipo de base de datos","Framework frontend"]', 0, 'El patrón Saga maneja transacciones distribuidas mediante una secuencia de transacciones locales'),
('Desarrollo', 3, '¿Qué es Domain-Driven Design (DDD)?', '["Enfoque de diseño centrado en el dominio del negocio","Lenguaje de programación","Herramienta de testing"]', 0, 'DDD es una metodología que pone el dominio del negocio en el centro del diseño del software'),
('Desarrollo', 3, '¿Qué es Circuit Breaker?', '["Patrón de resiliencia que previene cascadas de fallos","Compilador","Sistema operativo"]', 0, 'Circuit Breaker detecta fallos y previene que la aplicación intente operaciones que probablemente fallen'),
('Desarrollo', 3, '¿Qué es WebAssembly?', '["Formato binario para ejecutar código de alto rendimiento en navegadores","Base de datos","Framework CSS"]', 0, 'WebAssembly permite ejecutar código compilado de múltiples lenguajes en navegadores web con alto rendimiento'),
('Desarrollo', 3, '¿Qué es Kubernetes?', '["Sistema de orquestación de contenedores","Editor de código","Lenguaje de consultas"]', 0, 'Kubernetes automatiza el despliegue, escalado y gestión de aplicaciones en contenedores'),
('Desarrollo', 3, '¿Qué es la arquitectura hexagonal?', '["Arquitectura que aísla la lógica de negocio de dependencias externas","Framework web","Base de datos"]', 0, 'La arquitectura hexagonal (Ports & Adapters) separa la lógica core de las dependencias externas'),
('Desarrollo', 3, '¿Qué es eventual consistency?', '["Modelo donde los datos se sincronizan eventualmente, no inmediatamente","Algoritmo de ordenamiento","Patrón de diseño UI"]', 0, 'Consistencia eventual garantiza que todos los nodos tendrán los mismos datos eventualmente, no de inmediato');

-- =============================================================================
-- ÁREA 2: ANÁLISIS TI (30 preguntas)
-- =============================================================================

-- ANÁLISIS TI - Nivel Básico (1) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Analisis TI', 1, '¿Qué es un requerimiento funcional?', '["Describe QUÉ debe hacer el sistema","Describe cómo debe funcionar internamente","Un tipo de prueba"]', 0, 'Los requerimientos funcionales especifican las funcionalidades que el sistema debe proporcionar'),
('Analisis TI', 1, '¿Qué es un diagrama de flujo?', '["Representación gráfica de un proceso","Tipo de base de datos","Lenguaje de programación"]', 0, 'Un diagrama de flujo muestra visualmente los pasos de un proceso o algoritmo'),
('Analisis TI', 1, '¿Qué es un stakeholder?', '["Persona con interés en el proyecto","Desarrollador senior","Herramienta de gestión"]', 0, 'Stakeholder es cualquier persona u organización afectada o interesada en el proyecto'),
('Analisis TI', 1, '¿Qué es una historia de usuario?', '["Descripción informal de una funcionalidad desde perspectiva del usuario","Documento técnico","Reporte de error"]', 0, 'Una user story describe una funcionalidad en lenguaje simple desde el punto de vista del usuario'),
('Analisis TI', 1, '¿Qué significa MVP?', '["Minimum Viable Product - producto mínimo viable","Maximum Value Product","Multi Version Platform"]', 0, 'MVP es un producto con características mínimas suficientes para validar una idea con usuarios reales'),
('Analisis TI', 1, '¿Qué es un caso de uso?', '["Descripción de cómo un usuario interactúa con el sistema","Tipo de prueba","Modelo de datos"]', 0, 'Un caso de uso describe una interacción entre un actor y el sistema para lograr un objetivo'),
('Analisis TI', 1, '¿Qué es UML?', '["Unified Modeling Language - lenguaje de modelado","Framework de desarrollo","Base de datos"]', 0, 'UML es un lenguaje estándar para visualizar, especificar y documentar sistemas de software'),
('Analisis TI', 1, '¿Qué es un mockup?', '["Representación visual estática de la interfaz","Servidor de pruebas","Tipo de base de datos"]', 0, 'Un mockup es un prototipo visual que muestra cómo se verá la interfaz de usuario'),
('Analisis TI', 1, '¿Qué es el alcance de un proyecto?', '["Define qué está incluido y qué no en el proyecto","Presupuesto total","Equipo de desarrollo"]', 0, 'El alcance delimita los límites del proyecto, especificando qué se entregará y qué queda fuera'),
('Analisis TI', 1, '¿Qué es un bug?', '["Error o defecto en el software","Característica nueva","Mejora de rendimiento"]', 0, 'Un bug es un error en el código que causa que el software no funcione como se esperaba');

-- ANÁLISIS TI - Nivel Intermedio (2) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Analisis TI', 2, '¿Qué es el análisis de brecha (gap analysis)?', '["Identifica diferencias entre estado actual y deseado","Análisis de rendimiento","Prueba de seguridad"]', 0, 'Gap analysis identifica y documenta las brechas entre la situación actual y los objetivos deseados'),
('Analisis TI', 2, '¿Qué es un diagrama de contexto?', '["Muestra el sistema y sus interacciones externas de alto nivel","Muestra el código fuente","Muestra la base de datos"]', 0, 'El diagrama de contexto muestra el sistema como una caja negra con sus interacciones externas'),
('Analisis TI', 2, '¿Qué es la trazabilidad de requerimientos?', '["Capacidad de rastrear requerimientos a lo largo del ciclo de vida","Herramienta de debugging","Metodología ágil"]', 0, 'Trazabilidad permite seguir un requerimiento desde su origen hasta su implementación y pruebas'),
('Analisis TI', 2, '¿Qué es un wireframe?', '["Boceto de baja fidelidad de la interfaz de usuario","Base de datos","Framework"]', 0, 'Un wireframe es un esquema visual básico que muestra la estructura y disposición de elementos en una pantalla'),
('Analisis TI', 2, '¿Qué es BPMN?', '["Business Process Model and Notation - notación para modelar procesos","Lenguaje de programación","Base de datos"]', 0, 'BPMN es un estándar gráfico para modelar procesos de negocio de forma comprensible'),
('Analisis TI', 2, '¿Qué es el análisis FODA/SWOT?', '["Análisis de Fortalezas, Oportunidades, Debilidades, Amenazas","Metodología de desarrollo","Herramienta de testing"]', 0, 'FODA/SWOT analiza factores internos (fortalezas/debilidades) y externos (oportunidades/amenazas)'),
('Analisis TI', 2, '¿Qué son los criterios de aceptación?', '["Condiciones que debe cumplir una funcionalidad para ser aceptada","Pruebas automatizadas","Documentación técnica"]', 0, 'Los criterios de aceptación definen cuándo una historia de usuario está completa y cumple expectativas'),
('Analisis TI', 2, '¿Qué es un diagrama de secuencia?', '["Muestra interacciones entre objetos en orden temporal","Muestra la estructura de datos","Muestra el flujo de usuario"]', 0, 'El diagrama de secuencia representa la interacción entre objetos mostrando mensajes en orden cronológico'),
('Analisis TI', 2, '¿Qué es el backlog del producto?', '["Lista priorizada de todo el trabajo pendiente","Historial de errores","Documentación del código"]', 0, 'El product backlog es una lista ordenada de todo lo que podría necesitarse en el producto'),
('Analisis TI', 2, '¿Qué es un KPI?', '["Key Performance Indicator - indicador clave de rendimiento","Framework de desarrollo","Tipo de base de datos"]', 0, 'Un KPI es una métrica cuantificable que mide el éxito en alcanzar objetivos clave');

-- ANÁLISIS TI - Nivel Avanzado (3) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Analisis TI', 3, '¿Qué es el método MoSCoW?', '["Técnica de priorización: Must, Should, Could, Won''t","Framework de desarrollo","Lenguaje de modelado"]', 0, 'MoSCoW prioriza requerimientos en Must have, Should have, Could have, Won''t have'),
('Analisis TI', 3, '¿Qué es el análisis de impacto?', '["Evaluación de consecuencias de un cambio propuesto","Análisis de rendimiento","Prueba de carga"]', 0, 'Impact analysis evalúa cómo un cambio afectará a otros componentes del sistema'),
('Analisis TI', 3, '¿Qué es TOGAF?', '["Framework de arquitectura empresarial","Lenguaje de programación","Base de datos"]', 0, 'TOGAF (The Open Group Architecture Framework) es un marco para arquitectura empresarial'),
('Analisis TI', 3, '¿Qué es el análisis de causa raíz?', '["Técnica para identificar la causa fundamental de un problema","Análisis de requisitos","Diseño de interfaz"]', 0, 'Root Cause Analysis identifica la causa subyacente de problemas, no solo síntomas'),
('Analisis TI', 3, '¿Qué es un modelo de madurez de capacidades?', '["Framework que evalúa el nivel de madurez de procesos","Herramienta de testing","Metodología ágil"]', 0, 'Un modelo de madurez (como CMMI) evalúa la madurez de procesos organizacionales'),
('Analisis TI', 3, '¿Qué es el análisis de stakeholders?', '["Identificación y gestión de expectativas de partes interesadas","Análisis de código","Diseño de base de datos"]', 0, 'Stakeholder analysis identifica, clasifica y gestiona las expectativas de todas las partes interesadas'),
('Analisis TI', 3, '¿Qué es BABOK?', '["Business Analysis Body of Knowledge - guía para analistas de negocio","Framework de desarrollo","Lenguaje de programación"]', 0, 'BABOK es la guía estándar de conocimiento para la práctica de análisis de negocio'),
('Analisis TI', 3, '¿Qué es el análisis de costo-beneficio?', '["Evaluación de beneficios vs costos de una solución","Análisis de rendimiento","Prueba de seguridad"]', 0, 'Cost-benefit analysis compara costos de implementación con beneficios esperados'),
('Analisis TI', 3, '¿Qué es la ingeniería de requerimientos?', '["Proceso sistemático de elicitar, analizar, especificar y validar requerimientos","Desarrollo de software","Testing automatizado"]', 0, 'Requirements engineering es el proceso completo de descubrir, documentar y mantener requerimientos'),
('Analisis TI', 3, '¿Qué es el modelo de Kano?', '["Teoría que clasifica atributos según satisfacción del cliente","Framework ágil","Patrón de diseño"]', 0, 'El modelo de Kano clasifica características en básicas, de rendimiento y delighters');

-- =============================================================================
-- ÁREA 3: ADMINISTRACIÓN (30 preguntas)
-- =============================================================================

-- ADMINISTRACIÓN - Nivel Básico (1) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Administracion', 1, '¿Qué es un proyecto?', '["Esfuerzo temporal para crear un producto o servicio único","Tarea repetitiva","Proceso continuo"]', 0, 'Un proyecto es un esfuerzo temporal con un inicio y fin definidos para lograr un objetivo específico'),
('Administracion', 1, '¿Qué es un cronograma?', '["Calendario que muestra cuándo se realizarán las actividades","Lista de recursos","Presupuesto del proyecto"]', 0, 'El cronograma establece la línea temporal de actividades y sus fechas de inicio y fin'),
('Administracion', 1, '¿Qué es un hito (milestone)?', '["Punto significativo de progreso en el proyecto","Recurso necesario","Riesgo identificado"]', 0, 'Un milestone marca un punto importante de avance, generalmente sin duración'),
('Administracion', 1, '¿Qué es un entregable?', '["Producto o resultado verificable del proyecto","Reunión de equipo","Documento de planificación"]', 0, 'Un entregable es cualquier producto, resultado o capacidad única y verificable del proyecto'),
('Administracion', 1, '¿Qué es la gestión de proyectos?', '["Aplicación de conocimientos y técnicas para cumplir objetivos","Desarrollo de software","Análisis de datos"]', 0, 'Project management aplica conocimientos, habilidades y técnicas para satisfacer requisitos del proyecto'),
('Administracion', 1, '¿Qué es un equipo de proyecto?', '["Grupo de personas trabajando para lograr objetivos del proyecto","Departamento permanente","Comité ejecutivo"]', 0, 'El equipo de proyecto incluye a todas las personas con roles y responsabilidades en el proyecto'),
('Administracion', 1, '¿Qué es una reunión de estado?', '["Reunión para revisar progreso y problemas del proyecto","Sesión de capacitación","Entrevista de trabajo"]', 0, 'Status meeting revisa el avance, identifica bloqueos y coordina próximos pasos'),
('Administracion', 1, '¿Qué es un presupuesto?', '["Estimación de costos aprobada para el proyecto","Lista de tareas","Cronograma detallado"]', 0, 'El presupuesto es la suma de costos estimados y aprobados para realizar el proyecto'),
('Administracion', 1, '¿Qué es un recurso en gestión de proyectos?', '["Personas, equipos o materiales necesarios","Documento de planificación","Metodología ágil"]', 0, 'Los recursos incluyen personas, equipamiento, servicios y materiales necesarios para el proyecto'),
('Administracion', 1, '¿Qué es el cierre de proyecto?', '["Fase final donde se completa y documenta todo el trabajo","Primera reunión","Planificación inicial"]', 0, 'El cierre finaliza formalmente todas las actividades y documenta lecciones aprendidas');

-- ADMINISTRACIÓN - Nivel Intermedio (2) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Administracion', 2, '¿Qué es la ruta crítica?', '["Secuencia de actividades que determina la duración mínima del proyecto","Lista de riesgos","Presupuesto máximo"]', 0, 'La ruta crítica es la secuencia más larga de actividades que define la duración total del proyecto'),
('Administracion', 2, '¿Qué es un diagrama de Gantt?', '["Gráfico de barras que muestra el cronograma del proyecto","Diagrama de flujo","Organigrama"]', 0, 'El diagrama de Gantt muestra actividades del proyecto en barras a lo largo del tiempo'),
('Administracion', 2, '¿Qué es la gestión de riesgos?', '["Proceso de identificar, analizar y responder a riesgos","Control de calidad","Gestión de personal"]', 0, 'Risk management identifica amenazas y oportunidades para maximizar éxito del proyecto'),
('Administracion', 2, '¿Qué es un plan de comunicación?', '["Documento que define cómo se comunicará la información del proyecto","Cronograma de actividades","Lista de riesgos"]', 0, 'El communication plan establece qué, cuándo, cómo y a quién comunicar información del proyecto'),
('Administracion', 2, '¿Qué es el triángulo de hierro?', '["Relación entre alcance, tiempo y costo del proyecto","Estructura organizacional","Metodología de desarrollo"]', 0, 'El iron triangle muestra que cambios en alcance, tiempo o costo afectan a los otros dos'),
('Administracion', 2, '¿Qué es un sprint en Scrum?', '["Periodo corto de trabajo (1-4 semanas) para completar tareas","Reunión diaria","Retrospectiva"]', 0, 'Un sprint es una iteración de tiempo fijo donde el equipo completa un conjunto de trabajo'),
('Administracion', 2, '¿Qué es un registro de riesgos?', '["Documento que lista riesgos identificados y estrategias de respuesta","Lista de tareas","Cronograma"]', 0, 'El risk register documenta riesgos identificados, su análisis y planes de respuesta'),
('Administracion', 2, '¿Qué es la gestión del cambio?', '["Proceso para controlar y aprobar cambios en el proyecto","Capacitación de personal","Auditoría de calidad"]', 0, 'Change management asegura que los cambios sean evaluados, aprobados y documentados'),
('Administracion', 2, '¿Qué es un SLA?', '["Service Level Agreement - acuerdo de nivel de servicio","Software License Agreement","Strategic Leadership Approach"]', 0, 'Un SLA define niveles de servicio esperados entre proveedor y cliente'),
('Administracion', 2, '¿Qué es el valor ganado (EVM)?', '["Técnica que mide rendimiento del proyecto comparando trabajo planificado vs realizado","Presupuesto total","Lista de recursos"]', 0, 'Earned Value Management integra alcance, tiempo y costo para medir rendimiento del proyecto');

-- ADMINISTRACIÓN - Nivel Avanzado (3) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Administracion', 3, '¿Qué es PMBOK?', '["Project Management Body of Knowledge - guía estándar de PMI","Lenguaje de programación","Framework ágil"]', 0, 'PMBOK es la guía del PMI que define estándares y mejores prácticas en gestión de proyectos'),
('Administracion', 3, '¿Qué es la gestión de portafolio?', '["Gestión centralizada de múltiples proyectos alineados con estrategia","Gestión de un solo proyecto","Gestión de recursos humanos"]', 0, 'Portfolio management administra múltiples proyectos para optimizar recursos y alinearse con estrategia'),
('Administracion', 3, '¿Qué es SAFe?', '["Scaled Agile Framework - framework para escalar ágil en grandes organizaciones","Metodología tradicional","Herramienta de planificación"]', 0, 'SAFe proporciona patrones para implementar prácticas ágiles a escala empresarial'),
('Administracion', 3, '¿Qué es el análisis de Monte Carlo?', '["Técnica de simulación para evaluar riesgos usando probabilidades","Metodología de desarrollo","Proceso de testing"]', 0, 'Monte Carlo usa simulación estocástica para analizar impacto de riesgos en objetivos del proyecto'),
('Administracion', 3, '¿Qué es la gestión de stakeholders?', '["Proceso de identificar y gestionar expectativas de partes interesadas","Gestión de personal","Control de calidad"]', 0, 'Stakeholder management identifica y gestiona relaciones con todas las partes interesadas'),
('Administracion', 3, '¿Qué es PRINCE2?', '["PRojects IN Controlled Environments - metodología de gestión de proyectos","Framework de desarrollo","Base de datos"]', 0, 'PRINCE2 es una metodología estructurada de gestión de proyectos basada en procesos'),
('Administracion', 3, '¿Qué es la gestión del valor ganado?', '["Metodología que integra alcance, cronograma y recursos para medir rendimiento","Gestión de recursos humanos","Auditoría financiera"]', 0, 'EVM integra mediciones de alcance, cronograma y costos para evaluar rendimiento del proyecto'),
('Administracion', 3, '¿Qué es la gestión del conocimiento en proyectos?', '["Proceso de capturar, compartir y reutilizar conocimiento organizacional","Capacitación técnica","Documentación de código"]', 0, 'Knowledge management captura lecciones aprendidas y mejores prácticas para proyectos futuros'),
('Administracion', 3, '¿Qué es la teoría de restricciones (TOC)?', '["Metodología que identifica y gestiona el cuello de botella principal","Framework ágil","Patrón de diseño"]', 0, 'Theory of Constraints enfoca esfuerzos en la restricción que más limita el rendimiento del sistema'),
('Administracion', 3, '¿Qué es la gestión de la configuración?', '["Proceso de identificar y controlar cambios en artefactos del proyecto","Instalación de software","Configuración de servidores"]', 0, 'Configuration management mantiene la integridad de productos del proyecto a lo largo de su ciclo de vida');

-- =============================================================================
-- ÁREA 4: INGENIERÍA INFORMÁTICA (30 preguntas)
-- =============================================================================

-- INGENIERÍA INFORMÁTICA - Nivel Básico (1) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Ingenieria Informatica', 1, '¿Qué es una dirección IP?', '["Identificador numérico único de un dispositivo en red","Contraseña de acceso","Tipo de cable"]', 0, 'Una dirección IP identifica de forma única un dispositivo en una red TCP/IP'),
('Ingenieria Informatica', 1, '¿Qué es un servidor?', '["Computadora que proporciona servicios a otros dispositivos","Aplicación móvil","Base de datos"]', 0, 'Un servidor es una computadora que proporciona datos, recursos o servicios a otros dispositivos (clientes)'),
('Ingenieria Informatica', 1, '¿Qué es un firewall?', '["Sistema de seguridad que controla el tráfico de red","Editor de texto","Lenguaje de programación"]', 0, 'Un firewall filtra tráfico de red permitiendo o bloqueando comunicaciones según reglas de seguridad'),
('Ingenieria Informatica', 1, '¿Qué es una base de datos?', '["Colección organizada de datos estructurados","Aplicación web","Sistema operativo"]', 0, 'Una base de datos almacena y organiza datos de forma que puedan ser fácilmente accedidos y gestionados'),
('Ingenieria Informatica', 1, '¿Qué es un sistema operativo?', '["Software que gestiona hardware y software de una computadora","Aplicación de oficina","Navegador web"]', 0, 'Un OS gestiona recursos de hardware y proporciona servicios comunes para software de aplicación'),
('Ingenieria Informatica', 1, '¿Qué es el ancho de banda?', '["Capacidad máxima de transmisión de datos en una red","Tamaño de disco duro","Cantidad de RAM"]', 0, 'Bandwidth es la cantidad máxima de datos que pueden transmitirse en un canal de comunicación'),
('Ingenieria Informatica', 1, '¿Qué es un backup?', '["Copia de seguridad de datos","Actualización de software","Limpieza de archivos"]', 0, 'Un backup es una copia de datos almacenada para recuperación en caso de pérdida'),
('Ingenieria Informatica', 1, '¿Qué es HTTP?', '["Protocolo de transferencia de hipertexto","Lenguaje de marcado","Base de datos"]', 0, 'HTTP es el protocolo de comunicación usado para transferir información en la web'),
('Ingenieria Informatica', 1, '¿Qué es RAM?', '["Memoria de acceso aleatorio temporal","Disco duro","Tarjeta gráfica"]', 0, 'RAM es memoria volátil que almacena temporalmente datos e instrucciones que la CPU está usando'),
('Ingenieria Informatica', 1, '¿Qué es el DNS?', '["Sistema que traduce nombres de dominio a direcciones IP","Protocolo de seguridad","Tipo de servidor web"]', 0, 'DNS (Domain Name System) convierte nombres de dominio legibles en direcciones IP');

-- INGENIERÍA INFORMÁTICA - Nivel Intermedio (2) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Ingenieria Informatica', 2, '¿Qué es un balanceador de carga?', '["Dispositivo que distribuye tráfico entre múltiples servidores","Herramienta de monitoreo","Firewall avanzado"]', 0, 'Un load balancer distribuye solicitudes entrantes entre varios servidores para optimizar rendimiento'),
('Ingenieria Informatica', 2, '¿Qué es RAID?', '["Redundant Array of Independent Disks - tecnología de almacenamiento redundante","Protocolo de red","Sistema de archivos"]', 0, 'RAID combina múltiples discos en una unidad lógica para redundancia y/o rendimiento'),
('Ingenieria Informatica', 2, '¿Qué es una VPN?', '["Virtual Private Network - red privada sobre internet pública","Servidor web","Base de datos distribuida"]', 0, 'Una VPN crea una conexión segura y cifrada sobre una red pública como internet'),
('Ingenieria Informatica', 2, '¿Qué es el modelo OSI?', '["Modelo de 7 capas para sistemas de comunicación en red","Framework de desarrollo","Metodología ágil"]', 0, 'El modelo OSI define 7 capas que describen cómo datos viajan de una aplicación a otra a través de red'),
('Ingenieria Informatica', 2, '¿Qué es SSH?', '["Secure Shell - protocolo para acceso remoto seguro","Sistema de archivos","Lenguaje de scripting"]', 0, 'SSH proporciona un canal seguro para acceder y administrar sistemas remotamente'),
('Ingenieria Informatica', 2, '¿Qué es un contenedor?', '["Paquete ligero de software con código y dependencias","Servidor físico","Base de datos"]', 0, 'Un contenedor empaqueta aplicación y dependencias en una unidad portable y aislada'),
('Ingenieria Informatica', 2, '¿Qué es CI/CD en infraestructura?', '["Continuous Integration/Deployment para automatizar despliegues","Modelo de red","Protocolo de seguridad"]', 0, 'CI/CD automatiza integración de código, pruebas y despliegue a producción'),
('Ingenieria Informatica', 2, '¿Qué es un proxy?', '["Servidor intermediario que actúa en nombre de clientes","Firewall","Balanceador de carga"]', 0, 'Un proxy server actúa como intermediario entre clientes y servidores, añadiendo funcionalidades'),
('Ingenieria Informatica', 2, '¿Qué es monitoreo de infraestructura?', '["Seguimiento continuo del rendimiento y salud de sistemas","Backup de datos","Instalación de software"]', 0, 'Monitoring rastrea métricas de rendimiento, disponibilidad y salud de sistemas IT'),
('Ingenieria Informatica', 2, '¿Qué es alta disponibilidad (HA)?', '["Capacidad de un sistema de operar continuamente sin fallos","Velocidad de procesamiento","Capacidad de almacenamiento"]', 0, 'High Availability asegura que sistemas operen continuamente minimizando downtime');

-- INGENIERÍA INFORMÁTICA - Nivel Avanzado (3) - 10 preguntas
INSERT INTO app.pregunta_nivelacion (habilidad, dificultad, enunciado, opciones, respuesta_correcta, explicacion) VALUES
('Ingenieria Informatica', 3, '¿Qué es Infrastructure as Code (IaC)?', '["Gestión de infraestructura mediante archivos de configuración versionados","Lenguaje de programación","Framework de desarrollo"]', 0, 'IaC gestiona y provisiona infraestructura mediante código en lugar de procesos manuales'),
('Ingenieria Informatica', 3, '¿Qué es un service mesh?', '["Capa de infraestructura para gestionar comunicación entre microservicios","Base de datos distribuida","Framework de desarrollo"]', 0, 'Un service mesh gestiona comunicación, seguridad y observabilidad entre microservicios'),
('Ingenieria Informatica', 3, '¿Qué es zero trust security?', '["Modelo de seguridad que no confía en ninguna entidad por defecto","Firewall tradicional","Antivirus"]', 0, 'Zero Trust asume que amenazas existen dentro y fuera de la red, verificando siempre'),
('Ingenieria Informatica', 3, '¿Qué es chaos engineering?', '["Disciplina de experimentar con sistemas para descubrir debilidades","Metodología de desarrollo","Técnica de hacking"]', 0, 'Chaos Engineering inyecta fallos controlados para mejorar la resiliencia del sistema'),
('Ingenieria Informatica', 3, '¿Qué es edge computing?', '["Procesamiento de datos cerca de la fuente en lugar del centro de datos","Servidor en la nube","Base de datos distribuida"]', 0, 'Edge computing procesa datos cerca del origen para reducir latencia y uso de ancho de banda'),
('Ingenieria Informatica', 3, '¿Qué es observability?', '["Capacidad de comprender estado interno de un sistema basándose en outputs","Monitoreo básico","Logging"]', 0, 'Observability permite entender el estado interno usando logs, métricas y traces'),
('Ingenieria Informatica', 3, '¿Qué es SRE (Site Reliability Engineering)?', '["Disciplina que aplica principios de ingeniería de software a operaciones","Metodología de desarrollo","Framework de testing"]', 0, 'SRE combina desarrollo de software con operaciones IT para crear sistemas escalables y confiables'),
('Ingenieria Informatica', 3, '¿Qué es multi-cloud?', '["Uso de múltiples proveedores de nube simultáneamente","Servidor local","Base de datos NoSQL"]', 0, 'Multi-cloud distribuye aplicaciones y datos entre múltiples proveedores de nube'),
('Ingenieria Informatica', 3, '¿Qué es GitOps?', '["Práctica de usar Git como fuente de verdad para infraestructura y aplicaciones","Lenguaje de programación","Framework frontend"]', 0, 'GitOps usa Git para declarar el estado deseado de sistemas y automatizar despliegues'),
('Ingenieria Informatica', 3, '¿Qué es la arquitectura serverless?', '["Modelo donde el proveedor gestiona servidores y el usuario solo provee código","Sin servidores físicos","Base de datos sin esquema"]', 0, 'Serverless abstrae la gestión de servidores, permitiendo a desarrolladores enfocarse solo en código');

-- =============================================================================
-- VERIFICACIÓN Y RESUMEN
-- =============================================================================

-- Contar preguntas insertadas por área y dificultad
SELECT
    habilidad,
    dificultad,
    COUNT(*) as total_preguntas
FROM app.pregunta_nivelacion
GROUP BY habilidad, dificultad
ORDER BY habilidad, dificultad;

-- Resumen total
SELECT
    COUNT(*) as total_preguntas,
    COUNT(DISTINCT habilidad) as total_areas
FROM app.pregunta_nivelacion;

COMMIT;

-- =============================================================================
-- RESUMEN DE LA MIGRACIÓN:
-- - 120 preguntas de opción múltiple insertadas
-- - 4 áreas: Desarrollo, Análisis TI, Administración, Ingeniería Informática
-- - 3 niveles de dificultad por área: Básico (1), Intermedio (2), Avanzado (3)
-- - 10 preguntas por cada combinación de área y nivel
-- - Todas con explicaciones educativas
-- =============================================================================
