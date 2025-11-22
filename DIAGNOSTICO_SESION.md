# Diagnóstico del Error 404 - Sesión No Encontrada

## 🔍 Problema Identificado

```
❌ [RESPONDER] Sesión 61a99683-5ccf-4952-9437-ccdd3cb5d3cf NO EXISTE en BD
```

**La sesión con ID `61a99683-5ccf-4952-9437-ccdd3cb5d3cf` no existe en la tabla `app.sesion_entrevista`.**

---

## 🔎 Verificaciones Necesarias

### 1. Verificar si la sesión existe en la base de datos

Ejecuta esta consulta en PostgreSQL:

```sql
SELECT *
FROM app.sesion_entrevista
WHERE sesion_id = '61a99683-5ccf-4952-9437-ccdd3cb5d3cf';
```

**Resultado esperado:**
- Si retorna **0 filas** → La sesión no existe (problema confirmado)
- Si retorna **1 fila** → La sesión existe pero hay un problema con el repositorio

---

### 2. Verificar el esquema de la tabla

```sql
\d app.sesion_entrevista
```

**Verifica que tenga:**
- Columna `sesion_id` (UUID, PRIMARY KEY)
- Columna `usuario_id` (UUID, FK)
- Columnas `modo`, `nivel`, `fecha_inicio`, etc.

---

### 3. Listar todas las sesiones del usuario

```sql
SELECT sesion_id, usuario_id, modo, nivel, fecha_inicio, fecha_fin
FROM app.sesion_entrevista
WHERE usuario_id = 'aaaac8ed-5a65-4427-a263-f7323c37e146'
ORDER BY fecha_inicio DESC
LIMIT 10;
```

---

## 🎯 Posibles Causas

### **Causa 1: La sesión fue creada en otra base de datos**
- El endpoint `POST /sesiones` creó la sesión en una BD diferente
- Estás usando un entorno de desarrollo local vs. producción

**Solución:** Verifica la configuración de conexión a BD en `application.conf` o variables de entorno.

---

### **Causa 2: La sesión nunca se creó**
- El request de creación falló silenciosamente
- Hubo un rollback en la transacción

**Solución:** Crea una nueva sesión usando el endpoint correcto:

```bash
curl -X POST http://localhost:8080/sesiones \
  -H "Authorization: Bearer {TU_JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "modo": "tec",
    "nivel": "mid"
  }'
```

**Logs esperados:**
```
🆕 [CREAR_SESION] Usuario aaaac8ed-5a65-4427-a263-f7323c37e146 creando sesión: modo=tec, nivel=mid
✅ [CREAR_SESION] Sesión creada exitosamente: {NUEVO_UUID}
```

---

### **Causa 3: Problema con el nombre de la tabla en Exposed**

Verificar que la tabla Exposed apunta al esquema correcto:

```kotlin
// Archivo: SesionEntrevistaTable.kt
object SesionEntrevistaTable : Table("app.sesion_entrevista") {  // ✅ CORRECTO
    val sesionId = uuid("sesion_id")  // ✅ Nombre de columna correcto
    ...
}
```

**Verificación:**
- ✅ Nombre de tabla: `app.sesion_entrevista` (con esquema `app`)
- ✅ Nombre de columna PK: `sesion_id` (snake_case)

---

### **Causa 4: Transacción no confirmada (commit)**

El repositorio usa `newSuspendedTransaction`, pero podría no estar haciendo commit.

**Verificación en SesionEntrevistaRepository.kt:**
```kotlin
suspend fun create(...): SesionEntrevista = newSuspendedTransaction(db = db) {
    val newId = UUID.randomUUID()

    SesionEntrevistaTable.insert { st ->
        st[SesionEntrevistaTable.sesionId] = newId
        // ... otros campos
    }

    // ✅ IMPORTANTE: Retornar el objeto para confirmar la transacción
    SesionEntrevista(
        sesionId = newId,
        ...
    )
}
```

---

## 🧪 Test Completo del Flujo

Ejecuta estos pasos en orden:

### **Paso 1: Login**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "correo": "nico1@correo.com",
    "contrasena": "tu_password"
  }'
```

**Guarda el JWT token de la respuesta.**

---

### **Paso 2: Crear Sesión**
```bash
curl -X POST http://localhost:8080/sesiones \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "modo": "tec",
    "nivel": "mid"
  }'
```

**Respuesta esperada:**
```json
{
  "sessionId": "nuevo-uuid-generado",
  "modo": "tec",
  "nivel": "mid",
  "fechaInicio": "2025-11-19T..."
}
```

**Guarda el `sessionId` de la respuesta.**

---

### **Paso 3: Obtener Pregunta**
```bash
curl -X POST http://localhost:8080/sesiones/{sessionId}/preguntas \
  -H "Authorization: Bearer {JWT_TOKEN}"
```

**Respuesta esperada:**
```json
{
  "sessionPreguntaId": "uuid-de-session-pregunta",
  "preguntaId": "uuid-de-pregunta",
  "texto": "¿Qué es un closure?",
  "pistas": {...},
  "orden": 1
}
```

**Guarda el `sessionPreguntaId` de la respuesta.**

---

### **Paso 4: Responder Pregunta**
```bash
curl -X POST http://localhost:8080/sesiones/{sessionId}/responder \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "sessionPreguntaId": "{sessionPreguntaId}",
    "texto": "Un closure es una función que..."
  }'
```

**Respuesta esperada: Feedback exitoso (200 OK)**

---

## ⚡ Solución Rápida

**El problema es que estás usando un `sessionId` que no existe en la BD.**

### Opción 1: Crear una nueva sesión
1. Haz `POST /sesiones` para crear una sesión nueva
2. Usa el `sessionId` que te devuelve la respuesta
3. Continúa con el flujo normal

### Opción 2: Verificar sesiones existentes

```sql
-- Ver sesiones del usuario
SELECT sesion_id, modo, nivel, fecha_inicio
FROM app.sesion_entrevista
WHERE usuario_id = 'aaaac8ed-5a65-4427-a263-f7323c37e146'
AND fecha_fin IS NULL  -- Solo sesiones activas
ORDER BY fecha_inicio DESC;
```

Usa un `sesion_id` de la consulta anterior.

---

## 📊 Logs para Monitorear

Cuando hagas el flujo completo, deberías ver estos logs:

```
# 1. Crear sesión
🆕 [CREAR_SESION] Usuario aaaac8ed... creando sesión: modo=tec, nivel=mid
✅ [CREAR_SESION] Sesión creada exitosamente: {NUEVO_UUID}

# 2. Obtener pregunta
📝 [PREGUNTAS] Usuario aaaac8ed... solicitando pregunta para sesión {NUEVO_UUID}
✅ [PREGUNTAS] Sesión encontrada. Usuario dueño: aaaac8ed...
📊 [PREGUNTAS] Preguntas ya usadas: 0
🔎 [PREGUNTAS] Buscando siguiente pregunta: modo=tec, nivel=mid
✅ [PREGUNTAS] Pregunta seleccionada: {preguntaId}
✅ [PREGUNTAS] session_pregunta creada: {sessionPreguntaId}, orden=1

# 3. Responder pregunta
🔍 [RESPONDER] Usuario aaaac8ed... intentando responder en sesión {NUEVO_UUID}
📝 [RESPONDER] sessionPreguntaId recibido: {sessionPreguntaId}
🔎 [RESPONDER] Buscando sesión en BD: {NUEVO_UUID}
✅ [RESPONDER] Sesión encontrada. Usuario dueño: aaaac8ed...
✅ [RESPONDER] Validación de sesión exitosa
🔎 [RESPONDER] Buscando session_pregunta: {sessionPreguntaId}
✅ [RESPONDER] session_pregunta encontrada
✅ [RESPONDER] Todas las validaciones pasadas. Creando respuesta...
```

---

## 🎯 Conclusión

**El error es claro: estás intentando responder a una sesión que no existe en la base de datos.**

**Acción inmediata:**
1. Crea una nueva sesión con `POST /sesiones`
2. Usa el `sessionId` que te devuelve
3. Continúa con el flujo normalmente

Si el problema persiste después de crear una nueva sesión, verifica:
- Configuración de la base de datos
- Que las tablas estén en el esquema `app`
- Que la migración se haya ejecutado correctamente
