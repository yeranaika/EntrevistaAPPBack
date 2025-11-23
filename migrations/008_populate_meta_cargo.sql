-- Migración 008: Poblar campo meta_cargo en preguntas de nivelación
-- Asigna cargos específicos basados en el sector (área)

-- ========================================
-- DESARROLLO
-- ========================================

-- Desarrollador Full Stack
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- También crear alias para otros cargos de desarrollo
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Frontend'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND texto ILIKE '%frontend%'
  AND activa = true;

UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Backend'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND texto ILIKE '%backend%'
  AND activa = true;

-- ========================================
-- ANALISTA TI
-- ========================================

UPDATE app.pregunta
SET meta_cargo = 'Analista de Sistemas'
WHERE tipo_banco = 'NV'
  AND sector = 'Analista TI'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- ========================================
-- ADMINISTRACIÓN
-- ========================================

UPDATE app.pregunta
SET meta_cargo = 'Project Manager'
WHERE tipo_banco = 'NV'
  AND sector = 'Administracion'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- ========================================
-- INGENIERÍA INFORMÁTICA
-- ========================================

UPDATE app.pregunta
SET meta_cargo = 'Ingeniero DevOps'
WHERE tipo_banco = 'NV'
  AND sector = 'Ingenieria Informatica'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- ========================================
-- VERIFICACIÓN
-- ========================================

-- Ver distribución de preguntas por cargo
SELECT 
    meta_cargo,
    nivel,
    COUNT(*) as total_preguntas
FROM app.pregunta
WHERE tipo_banco = 'NV'
  AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY meta_cargo, nivel;

-- Ver preguntas sin cargo asignado
SELECT 
    pregunta_id,
    sector,
    nivel,
    LEFT(texto, 50) as preview
FROM app.pregunta
WHERE tipo_banco = 'NV'
  AND activa = true
  AND (meta_cargo IS NULL OR meta_cargo = '')
LIMIT 10;
