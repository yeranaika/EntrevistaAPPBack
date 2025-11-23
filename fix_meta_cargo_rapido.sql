-- ========================================
-- FIX RÁPIDO: Poblar meta_cargo
-- Copiar y pegar en tu cliente SQL
-- ========================================

-- Paso 1: Ver estado actual (ANTES)
SELECT 
    'ANTES DE ACTUALIZAR' as momento,
    sector,
    COUNT(*) as total_preguntas,
    COUNT(CASE WHEN meta_cargo IS NULL OR meta_cargo = '' THEN 1 END) as sin_cargo
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY sector;

-- Paso 2: Actualizar preguntas
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

UPDATE app.pregunta
SET meta_cargo = 'Analista de Sistemas'
WHERE tipo_banco = 'NV'
  AND sector = 'Analista TI'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

UPDATE app.pregunta
SET meta_cargo = 'Project Manager'
WHERE tipo_banco = 'NV'
  AND sector = 'Administracion'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

UPDATE app.pregunta
SET meta_cargo = 'Ingeniero DevOps'
WHERE tipo_banco = 'NV'
  AND sector = 'Ingenieria Informatica'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- Paso 3: Verificar resultado (DESPUÉS)
SELECT 
    'DESPUÉS DE ACTUALIZAR' as momento,
    meta_cargo,
    nivel,
    COUNT(*) as total_preguntas
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY meta_cargo, nivel;

-- Paso 4: Verificar que no queden preguntas sin cargo
SELECT 
    CASE 
        WHEN COUNT(*) = 0 THEN '✅ TODAS LAS PREGUNTAS TIENEN CARGO'
        ELSE '❌ AÚN HAY ' || COUNT(*) || ' PREGUNTAS SIN CARGO'
    END as resultado
FROM app.pregunta
WHERE tipo_banco = 'NV' 
  AND activa = true
  AND (meta_cargo IS NULL OR meta_cargo = '');
