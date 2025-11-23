# 🚀 Instrucciones Rápidas - Tests por Cargo

## ✅ Cambios Completados

Los tests de nivelación ahora funcionan por **cargo de trabajo** en lugar de área.

---

## 📋 Qué Hacer Ahora (3 pasos)

### 1️⃣ Ejecutar Migración SQL

```bash
# Conectar a PostgreSQL
psql -U tu_usuario -d tu_base_de_datos

# Ejecutar migración
\i migrations/008_populate_meta_cargo.sql
```

O copiar y pegar el contenido del archivo en tu cliente SQL.

**Esto asigna cargos a las preguntas existentes:**
- Desarrollo → "Desarrollador Full Stack"
- Analista TI → "Analista de Sistemas"
- Administracion → "Project Manager"
- Ingenieria Informatica → "Ingeniero DevOps"

### 2️⃣ Re-importar Postman

1. Abrir Postman
2. Click derecho en colección antigua → **Delete**
3. **Import** → Arrastrar `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`
4. Seleccionar environment: **"EntrevistaAPP - Local"**

### 3️⃣ Probar

**Opción A: Postman**
```
1. Auth → Register
2. Onboarding → POST Onboarding
   Body: {
     "area": "Desarrollo",
     "nivelExperiencia": "Junior",
     "nombreCargo": "Desarrollador Full Stack"
   }
3. Tests → GET - Iniciar Test por Cargo
   (usa: cargo=Desarrollador Full Stack)
4. Tests → POST - Evaluar Test
   (el cargo se usa automáticamente)
```

**Opción B: Script**
```powershell
.\test-api.ps1
```

---

## 🔍 Verificar que Funciona

### En Postman:
1. Ejecutar "Iniciar Test por Cargo"
2. Verificar en Console: "Preguntas guardadas para evaluación"
3. Verificar variable `test_cargo` en environment (ícono del ojo 👁️)
4. Ejecutar "Evaluar Test" - debería usar el cargo automáticamente

### En Base de Datos:
```sql
-- Ver preguntas por cargo
SELECT meta_cargo, nivel, COUNT(*) as total
FROM app.pregunta 
WHERE tipo_banco = 'NV' AND activa = true
GROUP BY meta_cargo, nivel
ORDER BY meta_cargo, nivel;
```

Deberías ver algo como:
```
meta_cargo                  | nivel | total
---------------------------+-------+-------
Desarrollador Full Stack   | jr    |   12
Desarrollador Full Stack   | mid   |   12
Desarrollador Full Stack   | sr    |    6
...
```

---

## 📝 Cambios Principales

### Endpoint Actualizado
```
Antes: GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10
Ahora:  GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
```

### Flujo Automático en Postman
1. "Iniciar Test" guarda el cargo en `{{test_cargo}}`
2. "Evaluar Test" usa `{{test_cargo}}` automáticamente
3. No necesitas copiar/pegar el cargo manualmente

---

## ⚠️ Si Algo Falla

### Error: "No hay suficientes preguntas disponibles"
**Causa:** Las preguntas no tienen `meta_cargo` poblado

**Solución:**
```sql
-- Verificar preguntas sin cargo
SELECT COUNT(*) 
FROM app.pregunta 
WHERE tipo_banco = 'NV' 
  AND activa = true 
  AND (meta_cargo IS NULL OR meta_cargo = '');

-- Si hay preguntas sin cargo, ejecutar migración 008
```

### Error: "Parámetro 'cargo' requerido"
**Causa:** Estás usando el endpoint antiguo con `habilidad`

**Solución:** Usar `cargo` en lugar de `habilidad`:
```
✅ ?cargo=Desarrollador Full Stack
❌ ?habilidad=Desarrollo
```

### Variable {{test_cargo}} vacía
**Causa:** El environment no está seleccionado o el script no se ejecutó

**Solución:**
1. Verificar environment seleccionado (arriba derecha)
2. Abrir Console de Postman (View → Show Postman Console)
3. Ejecutar "Iniciar Test" de nuevo
4. Verificar que aparezca: "Preguntas guardadas para evaluación"

---

## 📚 Documentación Completa

- `CAMBIO_CARGO_NIVELACION.md` - Explicación detallada de todos los cambios
- `RESUMEN_CAMBIOS_CARGO.md` - Resumen de archivos modificados
- `migrations/008_populate_meta_cargo.sql` - Script SQL para migración

---

## ✨ Beneficios

1. **Más específico:** Tests personalizados por cargo exacto
2. **Automático:** El cargo se guarda y usa automáticamente en Postman
3. **Flexible:** Puedes crear preguntas para cualquier cargo
4. **Compatible:** El formato JSON sigue siendo el mismo

---

## 🎯 Próximos Pasos (Opcional)

1. **Crear más cargos:**
   ```sql
   -- Ejemplo: Agregar preguntas para "Desarrollador Mobile"
   INSERT INTO app.pregunta (tipo_banco, sector, nivel, meta_cargo, ...)
   VALUES ('NV', 'Desarrollo', 'jr', 'Desarrollador Mobile', ...);
   ```

2. **Generar preguntas con IA:**
   - Usar el endpoint `/tests/nivelacion/generate-from-job`
   - Genera preguntas automáticamente para un cargo específico

3. **Actualizar frontend:**
   - Cambiar llamadas de API para usar `cargo`
   - Obtener el cargo desde el onboarding del usuario

---

**¿Todo listo?** Ejecuta la migración SQL y prueba en Postman! 🚀
