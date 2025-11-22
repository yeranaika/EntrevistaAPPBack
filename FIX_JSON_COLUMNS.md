# Fix: Error JSON Columns en Retroalimentación

## 🐛 **Problema Identificado**

```
ERROR: column "aciertos" is of type json but expression is of type character varying
```

**Causa:** La tabla PostgreSQL tiene columnas definidas como tipo `JSON`, pero Exposed estaba intentando insertar valores como `VARCHAR/TEXT`.

---

## ✅ **Solución Implementada**

### **1. Cambio en Exposed Table Definition**

**Archivo modificado:** [data/tables/sesiones/RetroalimentacionTable.kt](src/main/kotlin/data/tables/sesiones/RetroalimentacionTable.kt)

**Antes:**
```kotlin
class JsonColumnType : TextColumnType() {
    override fun sqlType(): String = "JSON"
}

val aciertos = jsonText("aciertos").nullable()   // ❌ Tipo JSON
val faltantes = jsonText("faltantes").nullable()  // ❌ Tipo JSON
```

**Después:**
```kotlin
val aciertos = text("aciertos").nullable()   // ✅ Tipo TEXT
val faltantes = text("faltantes").nullable()  // ✅ Tipo TEXT
```

**Beneficios:**
- Evita problemas de compatibilidad con tipos JSON de PostgreSQL
- Permite almacenar JSON serializado como string
- Más simple y directo

---

### **2. Migración SQL Creada**

**Archivo:** [migrations/005_fix_retroalimentacion_json_columns.sql](migrations/005_fix_retroalimentacion_json_columns.sql)

```sql
ALTER TABLE app.retroalimentacion
ALTER COLUMN aciertos TYPE TEXT;

ALTER TABLE app.retroalimentacion
ALTER COLUMN faltantes TYPE TEXT;
```

---

## 🚀 **Pasos para Aplicar la Solución**

### **Opción 1: Ejecutar Migración SQL (RECOMENDADO)**

En DBeaver o tu cliente PostgreSQL, ejecuta:

```sql
-- Conectarte a la base de datos
-- Luego ejecutar:

BEGIN;

ALTER TABLE app.retroalimentacion
ALTER COLUMN aciertos TYPE TEXT;

ALTER TABLE app.retroalimentacion
ALTER COLUMN faltantes TYPE TEXT;

COMMIT;
```

**Verificación:**
```sql
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'app'
  AND table_name = 'retroalimentacion'
  AND column_name IN ('aciertos', 'faltantes');
```

**Resultado esperado:**
```
column_name | data_type
------------|----------
aciertos    | text
faltantes   | text
```

---

### **Opción 2: Ejecutar desde CLI**

Si tienes acceso a `psql`:

```bash
psql -U postgres -d entrevistaapp -f migrations/005_fix_retroalimentacion_json_columns.sql
```

---

## 📊 **Verificación Post-Migración**

### **1. Verificar Tipos de Columnas**

```sql
\d app.retroalimentacion
```

Deberías ver:
```
Column               | Type | Collation | Nullable | Default
---------------------|------|-----------|----------|--------
retroalimentacion_id | uuid |           | not null |
respuesta_id         | uuid |           | not null |
nivel_feedback       | character varying(8) |    |          |
enunciado            | text |           |          |
aciertos             | text |           |          |  ← TEXT
faltantes            | text |           |          |  ← TEXT
```

---

### **2. Test del Endpoint**

Después de aplicar la migración y reiniciar el servidor:

**1. Crear sesión:**
```bash
POST http://localhost:8080/sesiones
Body: {"modo": "tec", "nivel": "mid"}
```

**2. Obtener pregunta:**
```bash
POST http://localhost:8080/sesiones/{sessionId}/preguntas
```

**3. Responder pregunta:**
```bash
POST http://localhost:8080/sesiones/{sessionId}/responder
Body: {
  "sessionPreguntaId": "{uuid}",
  "texto": "Tu respuesta..."
}
```

**Respuesta esperada (200 OK):**
```json
{
  "nivelFeedback": "free",
  "enunciado": "Tu respuesta cubre los aspectos básicos...",
  "aciertos": [
    "Explicación clara de conceptos principales",
    "Estructura ordenada de respuesta"
  ],
  "faltantes": [
    "Agregar ejemplos concretos",
    "Profundizar en casos de uso"
  ]
}
```

---

## 🔧 **Código del Repositorio**

El [RetroalimentacionRepository.kt](src/main/kotlin/data/repository/sesiones/RetroalimentacionRepository.kt) **ya funciona correctamente** con la solución:

```kotlin
suspend fun create(
    respuestaId: UUID,
    nivelFeedback: String,
    enunciado: String,
    aciertos: List<String>,
    faltantes: List<String>
): Retroalimentacion = newSuspendedTransaction(db = db) {
    val newId = UUID.randomUUID()

    RetroalimentacionTable.insert { st ->
        st[RetroalimentacionTable.retroalimentacionId] = newId
        st[RetroalimentacionTable.respuestaId] = respuestaId
        st[RetroalimentacionTable.nivelFeedback] = nivelFeedback
        st[RetroalimentacionTable.enunciado] = enunciado
        // ✅ Serializa a JSON string (TEXT en BD)
        st[RetroalimentacionTable.aciertos] = json.encodeToString(aciertos)
        st[RetroalimentacionTable.faltantes] = json.encodeToString(faltantes)
    }

    // ...
}
```

**Flujo:**
1. `aciertos: List<String>` → `json.encodeToString()` → `"[\"item1\",\"item2\"]"` (String)
2. Exposed inserta el String en columna TEXT
3. Al leer, deserializa: `json.decodeFromString<List<String>>(it)`

---

## ✅ **Estado del Build**

```
BUILD SUCCESSFUL in 21s
10 actionable tasks: 9 executed, 1 up-to-date
```

**Todo compila correctamente.**

---

## 📝 **Archivos Modificados**

1. ✅ [data/tables/sesiones/RetroalimentacionTable.kt](src/main/kotlin/data/tables/sesiones/RetroalimentacionTable.kt) - Cambiado de `jsonText()` a `text()`
2. ✅ [migrations/005_fix_retroalimentacion_json_columns.sql](migrations/005_fix_retroalimentacion_json_columns.sql) - Nueva migración SQL

---

## 🎯 **Próximos Pasos**

1. **Ejecuta la migración SQL** en PostgreSQL
2. **Reinicia el servidor** backend
3. **Prueba el flujo completo** de sesiones
4. **Verifica los logs** para confirmar que todo funciona

---

## 💡 **Nota Técnica**

### ¿Por qué TEXT en lugar de JSON?

**Ventajas de usar TEXT:**
- ✅ Más simple de manejar con Exposed
- ✅ Evita problemas de casting
- ✅ Compatible con serialización Kotlin
- ✅ Mismo almacenamiento en disco

**Desventajas:**
- ❌ No puedes usar queries JSON nativas de PostgreSQL (jsonb_path_query, etc.)
- ❌ No hay validación de formato JSON a nivel de BD

**Para este caso de uso (feedback serializado):**
- TEXT es suficiente porque solo insertamos y leemos datos completos
- No necesitamos queries dentro del JSON
- La validación de formato la hace kotlinx.serialization

Si en el futuro necesitas queries JSON avanzadas, puedes cambiar a tipo `JSONB` y usar la librería `exposed-json`.
