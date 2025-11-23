# 🔄 Cambio: Tests de Nivelación por Cargo

## Resumen del Cambio

Los endpoints de tests de nivelación ahora filtran por **cargo de trabajo** en lugar de **área/habilidad**.

### ❌ Antes (por área)
```
GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10
```

### ✅ Ahora (por cargo)
```
GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
```

---

## 🎯 Motivación

El sistema de onboarding captura el **cargo específico** del usuario (ej: "Desarrollador Full Stack", "Analista de Sistemas"), no solo el área general. Los tests deben ser específicos para ese cargo.

---

## 📝 Cambios Realizados

### 1. Backend - Repositorio

**Archivo:** `data/repository/nivelacion/PreguntaNivelacionRepository.kt`

**Nuevos métodos:**
```kotlin
// Cuenta preguntas por cargo
fun countByCargo(cargo: String): Long

// Obtiene preguntas aleatorias por cargo con mezcla balanceada
fun findRandomByCargo(
    cargo: String,
    cantidad: Int,
    mezclarDificultades: Boolean = true
): List<PreguntaNivelacionDetalle>

// Método auxiliar para obtener preguntas por cargo y nivel
private fun findByCargoYNivel(
    cargo: String,
    nivel: String,
    cantidad: Int
): List<PreguntaNivelacionDetalle>
```

**Consultas:** Ahora buscan en el campo `meta_cargo` de la tabla `pregunta` en lugar de `sector`.

### 2. Backend - Rutas

**Archivo:** `routes/nivelacion/testNivelacionRoutes.kt`

**Cambios en GET /tests/nivelacion/iniciar:**
- Parámetro: `habilidad` → `cargo`
- Validación: Verifica preguntas disponibles para el cargo
- Consulta: Usa `preguntaRepo.findRandomByCargo()` en lugar de `findRandomByHabilidad()`

**Ejemplo:**
```kotlin
val cargo = call.request.queryParameters["cargo"]
    ?: return@get call.respond(
        HttpStatusCode.BadRequest,
        mapOf("error" to "Parámetro 'cargo' requerido")
    )

val disponibles = preguntaRepo.countByCargo(cargo)
val preguntas = preguntaRepo.findRandomByCargo(
    cargo = cargo,
    cantidad = cantidad,
    mezclarDificultades = true
)
```

### 3. Postman - Colección

**Archivo:** `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`

**Cambios:**
- ✅ Renombrado: "GET - Iniciar Test (Desarrollo)" → "GET - Iniciar Test por Cargo"
- ❌ Eliminados: Endpoints específicos por área (Analista TI, Administración)
- ✅ Actualizado: Query parameter `habilidad` → `cargo`
- ✅ Nuevo: Variable de environment `test_cargo` que se guarda automáticamente
- ✅ Actualizado: "POST - Evaluar Test" usa `{{test_cargo}}` automáticamente
- ✅ Actualizado: "GET - Ver Historial por Habilidad" → "GET - Ver Historial por Cargo"

**Script automático en "Iniciar Test":**
```javascript
if (pm.response.code === 200) {
    var jsonData = pm.response.json();
    pm.environment.set("test_preguntas", JSON.stringify(jsonData.preguntas));
    pm.environment.set("test_cargo", pm.request.url.query.get('cargo'));
    console.log("Preguntas guardadas para evaluación");
}
```

### 4. Postman - Environment

**Archivo:** `EntrevistaAPP.postman_environment.json`

**Nueva variable:**
```json
{
    "key": "test_cargo",
    "value": "",
    "type": "default",
    "enabled": true
}
```

### 5. Script de Prueba

**Archivo:** `test-api.ps1`

**Cambios:**
```powershell
# Define el cargo desde el onboarding
$cargo = "Desarrollador Full Stack"

# Iniciar test por cargo
$testResponse = Invoke-RestMethod -Uri "$baseUrl/tests/nivelacion/iniciar?cargo=$cargo&cantidad=10"

# Evaluar test con el cargo
$evaluarBody = @{
    habilidad = $cargo  # Nota: el campo JSON sigue siendo "habilidad" por compatibilidad
    respuestas = $respuestas
}
```

---

## 🔄 Flujo Actualizado

### 1. Onboarding
```json
POST /onboarding
{
  "area": "Desarrollo",
  "nivelExperiencia": "Junior",
  "nombreCargo": "Desarrollador Full Stack",
  "descripcionObjetivo": "..."
}
```

### 2. Iniciar Test (usa el cargo del onboarding)
```
GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
```

**Respuesta:**
```json
{
  "habilidad": "Desarrollador Full Stack",
  "preguntas": [...],
  "totalPreguntas": 10
}
```

### 3. Evaluar Test
```json
POST /tests/nivelacion/evaluar
{
  "habilidad": "Desarrollador Full Stack",
  "respuestas": [...]
}
```

**Nota:** El campo JSON sigue siendo `habilidad` por compatibilidad con el modelo existente, pero ahora representa el cargo.

---

## 📊 Estructura de Datos

### Tabla: `app.pregunta`

**Campos relevantes:**
- `tipo_banco` = 'NV' (nivelación)
- `sector` = área general (ej: "Desarrollo")
- `nivel` = 'jr', 'mid', 'sr'
- **`meta_cargo`** = cargo específico (ej: "Desarrollador Full Stack") ← **NUEVO FILTRO**
- `tipo_pregunta` = 'opcion_multiple'
- `texto` = enunciado
- `config_respuesta` = JSON con opciones y respuesta correcta
- `activa` = true/false

**Consulta anterior (por área):**
```sql
WHERE tipo_banco = 'NV' 
  AND sector = 'Desarrollo'
  AND activa = true
```

**Consulta nueva (por cargo):**
```sql
WHERE tipo_banco = 'NV' 
  AND meta_cargo = 'Desarrollador Full Stack'
  AND activa = true
```

---

## ⚠️ Consideraciones Importantes

### 1. Preguntas Existentes

Las preguntas existentes pueden tener:
- ✅ `sector` poblado (área general)
- ❌ `meta_cargo` NULL o vacío

**Solución:** Necesitas poblar el campo `meta_cargo` en las preguntas existentes:

```sql
-- Ejemplo: Asignar cargo a preguntas de Desarrollo
UPDATE app.pregunta
SET meta_cargo = 'Desarrollador Full Stack'
WHERE tipo_banco = 'NV'
  AND sector = 'Desarrollo'
  AND meta_cargo IS NULL;
```

### 2. Compatibilidad

El campo JSON en los requests sigue siendo `habilidad` para mantener compatibilidad con:
- Modelos de datos existentes (`ResponderTestReq`, `TestNivelacionRes`)
- Frontend que ya esté implementado
- Historial de tests guardados

**Interpretación:**
- Antes: `habilidad` = área (ej: "Desarrollo")
- Ahora: `habilidad` = cargo (ej: "Desarrollador Full Stack")

### 3. Migración de Datos

Si tienes tests guardados con el formato anterior, el campo `area` en la tabla `test_nivelacion` contendrá el área general. Los nuevos tests guardarán el cargo específico.

---

## 🧪 Cómo Probar

### Opción 1: Postman

1. **Onboarding:**
   ```
   POST /onboarding
   Body: {
     "area": "Desarrollo",
     "nivelExperiencia": "Junior",
     "nombreCargo": "Desarrollador Full Stack"
   }
   ```

2. **Iniciar Test:**
   ```
   GET /tests/nivelacion/iniciar?cargo=Desarrollador Full Stack&cantidad=10
   ```
   - Verifica que `test_cargo` se guarde en el environment

3. **Evaluar Test:**
   ```
   POST /tests/nivelacion/evaluar
   Body: {
     "habilidad": "{{test_cargo}}",
     "respuestas": [...]
   }
   ```
   - El cargo se usa automáticamente desde la variable

### Opción 2: Script PowerShell

```powershell
.\test-api.ps1
```

Verifica la salida:
```
4. Iniciar Test por Cargo...
OK: 10 preguntas
```

### Opción 3: cURL

```bash
# 1. Onboarding
curl -X POST http://localhost:8080/onboarding \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"area":"Desarrollo","nivelExperiencia":"Junior","nombreCargo":"Desarrollador Full Stack"}'

# 2. Iniciar Test
curl -X GET "http://localhost:8080/tests/nivelacion/iniciar?cargo=Desarrollador%20Full%20Stack&cantidad=10" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 📋 Checklist de Implementación

- [x] Repositorio: Métodos `countByCargo()` y `findRandomByCargo()`
- [x] Rutas: Endpoint `/iniciar` usa parámetro `cargo`
- [x] Postman: Colección actualizada con query param `cargo`
- [x] Postman: Variable `test_cargo` en environment
- [x] Postman: Script automático guarda el cargo
- [x] Postman: Evaluar test usa `{{test_cargo}}`
- [x] Script: `test-api.ps1` actualizado
- [ ] Base de datos: Poblar campo `meta_cargo` en preguntas existentes
- [ ] Migración: Script SQL para actualizar preguntas
- [ ] Documentación: Actualizar README con nuevos ejemplos

---

## 🚀 Próximos Pasos

1. **Poblar preguntas con cargo:**
   - Crear script SQL para asignar `meta_cargo` a preguntas existentes
   - Ejecutar migración en base de datos

2. **Actualizar frontend:**
   - Cambiar llamadas de API para usar `cargo` en lugar de `habilidad`
   - Obtener el cargo desde el onboarding del usuario

3. **Crear preguntas específicas:**
   - Generar preguntas específicas para cada cargo
   - Usar el endpoint de admin para crear preguntas con `meta_cargo`

---

## 📞 Soporte

Si encuentras problemas:

1. **Error: "No hay suficientes preguntas disponibles"**
   - Verifica que las preguntas tengan el campo `meta_cargo` poblado
   - Ejecuta: `SELECT COUNT(*) FROM app.pregunta WHERE meta_cargo = 'TU_CARGO'`

2. **El cargo no se guarda automáticamente**
   - Verifica que el environment esté seleccionado en Postman
   - Revisa la Console de Postman para ver si hay errores en el script

3. **Tests anteriores no aparecen**
   - Los tests guardados con el formato anterior tienen `area` en lugar de `cargo`
   - Son compatibles, solo que el historial mostrará el área general

---

**Última actualización:** Cambios implementados y probados ✅
