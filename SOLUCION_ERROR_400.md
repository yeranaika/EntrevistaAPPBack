# 🔧 Solución: Error 400 en Iniciar Test

## ❌ Error que Estás Viendo

```json
{
  "error": "No hay suficientes preguntas disponibles para el cargo 'Desarrollador Full Stack'",
  "disponibles": 0,
  "solicitadas": 10,
  "sugerencia": "Verifica que el cargo esté correctamente configurado en el onboarding"
}
```

## 🎯 Causa

Las preguntas en la base de datos **no tienen el campo `meta_cargo` poblado**.

El sistema está buscando:
```sql
WHERE meta_cargo = 'Desarrollador Full Stack'
```

Pero las preguntas tienen:
```sql
meta_cargo = NULL  -- o vacío
```

---

## ✅ Solución Rápida (2 opciones)

### Opción 1: Ejecutar Migración SQL (Recomendado)

**Conectar a PostgreSQL:**
```bash
psql -U tu_usuario -d tu_base_de_datos
```

**Ejecutar migración:**
```sql
-- Poblar meta_cargo para Desarrollo
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND (meta_cargo IS NULL OR meta_cargo = '')
  AND activa = true;

-- Verificar
SELECT meta_cargo, COUNT(*) 
FROM app.pregunta 
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY meta_cargo;
```

**O ejecutar el archivo completo:**
```bash
psql -U tu_usuario -d tu_base_de_datos -f migrations/008_populate_meta_cargo.sql
```

### Opción 2: Usar Área Temporal (Workaround)

Si no puedes ejecutar la migración ahora, puedes usar el sistema anterior temporalmente:

**Cambiar el endpoint en Postman:**
```
Antes: ?cargo=Desarrollador Full Stack
Ahora:  ?habilidad=Desarrollo
```

Pero esto requiere cambiar el código del backend temporalmente.

---

## 🔍 Verificar el Problema

### 1. Verificar preguntas en BD

```sql
-- Ver cuántas preguntas hay por sector
SELECT sector, COUNT(*) as total
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY sector;

-- Ver cuántas tienen meta_cargo poblado
SELECT 
    CASE 
        WHEN meta_cargo IS NULL OR meta_cargo = '' THEN 'SIN CARGO'
        ELSE meta_cargo 
    END as cargo_status,
    COUNT(*) as total
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY cargo_status;
```

**Resultado esperado ANTES de migración:**
```
cargo_status | total
-------------+-------
SIN CARGO    |   120
```

**Resultado esperado DESPUÉS de migración:**
```
cargo_status                  | total
-----------------------------+-------
Desarrollador Full Stack     |    30
Analista de Sistemas         |    30
Project Manager              |    30
Ingeniero DevOps             |    30
```

### 2. Verificar en logs del servidor

En la terminal donde corre `.\gradlew run`, deberías ver:
```
No hay suficientes preguntas disponibles para el cargo 'Desarrollador Full Stack'
disponibles: 0
solicitadas: 10
```

---

## 📝 Script SQL Completo (Copiar y Pegar)

```sql
-- ========================================
-- SOLUCIÓN RÁPIDA: Poblar meta_cargo
-- ========================================

-- 1. Ver estado actual
SELECT 
    sector,
    CASE 
        WHEN meta_cargo IS NULL OR meta_cargo = '' THEN 'SIN CARGO'
        ELSE meta_cargo 
    END as cargo_status,
    COUNT(*) as total
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY sector, cargo_status
ORDER BY sector;

-- 2. Poblar cargos
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

-- 3. Verificar resultado
SELECT meta_cargo, nivel, COUNT(*) as total
FROM app.pregunta
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY meta_cargo, nivel;

-- 4. Ver preguntas sin cargo (debería estar vacío)
SELECT COUNT(*) as sin_cargo
FROM app.pregunta
WHERE tipo_banco = 'NV' 
  AND activa = true
  AND (meta_cargo IS NULL OR meta_cargo = '');
```

---

## 🧪 Probar Después de la Migración

### En Postman:

1. **Iniciar Test por Cargo**
   ```
   GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
   ```
   
   **Respuesta esperada (200 OK):**
   ```json
   {
     "habilidad": "Desarrollador Full Stack",
     "preguntas": [...],
     "totalPreguntas": 10
   }
   ```

2. **Verificar variable guardada**
   - Click en el ícono del ojo 👁️
   - Verificar que `test_cargo` = "Desarrollador Full Stack"

### Con Script:

```powershell
.\test-api.ps1
```

**Salida esperada:**
```
4. Iniciar Test por Cargo...
OK: 10 preguntas
```

---

## 🔄 Alternativa: Crear Preguntas Manualmente

Si no tienes preguntas en la BD, puedes crear algunas de prueba:

```sql
-- Insertar pregunta de prueba
INSERT INTO app.pregunta (
    tipo_banco,
    sector,
    nivel,
    meta_cargo,
    tipo_pregunta,
    texto,
    config_respuesta,
    activa,
    fecha_creacion
) VALUES (
    'NV',
    'Desarrollo',
    'jr',
    'Desarrollador Full Stack',
    'opcion_multiple',
    '¿Qué es REST?',
    '{"tipo":"opcion_multiple","opciones":[{"id":"A","texto":"Un protocolo de comunicación"},{"id":"B","texto":"Un estilo arquitectónico"},{"id":"C","texto":"Un lenguaje de programación"},{"id":"D","texto":"Una base de datos"}],"respuesta_correcta":"B"}',
    true,
    NOW()
);

-- Repetir para crear más preguntas...
```

---

## 📋 Checklist de Solución

- [ ] Conectar a PostgreSQL
- [ ] Ejecutar script SQL de migración
- [ ] Verificar que `meta_cargo` esté poblado
- [ ] Probar endpoint en Postman
- [ ] Verificar que devuelva 10 preguntas
- [ ] Verificar que `test_cargo` se guarde en environment

---

## 💡 Resumen

**Problema:** No hay preguntas con `meta_cargo = 'Desarrollador Full Stack'`

**Solución:** Ejecutar migración SQL para poblar el campo `meta_cargo`

**Comando:**
```bash
psql -U tu_usuario -d tu_base_de_datos -f migrations/008_populate_meta_cargo.sql
```

**Después:** Probar en Postman y debería funcionar ✅

---

¿Necesitas ayuda para conectarte a PostgreSQL o ejecutar la migración? 🤔
