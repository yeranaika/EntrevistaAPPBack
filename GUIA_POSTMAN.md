# 📮 Guía de Uso - Postman para EntrevistaAPP

## 🚀 Configuración Inicial

### 1. Importar la Colección y Entorno

1. **Abrir Postman**
2. **Importar Colección**:
   - Click en `Import` (arriba a la izquierda)
   - Seleccionar el archivo: `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`

3. **Importar Environment**:
   - Click en `Import`
   - Seleccionar el archivo: `EntrevistaAPP.postman_environment.json`

4. **Activar el Environment**:
   - En la esquina superior derecha, seleccionar `EntrevistaAPP - Local`

### 2. Verificar Servidor

Antes de comenzar, asegúrate de que el servidor esté corriendo:

```bash
# En la carpeta del proyecto
./gradlew run
```

Verifica con el endpoint:
- **GET** `http://localhost:8080/health` → Debe responder `OK`

---

## 📋 Flujo Completo de Pruebas

### **Paso 1: Autenticación** 🔐

#### 1.1 Registrar Usuario Nuevo
- **Carpeta**: `1. Auth` → `Register`
- **Método**: POST
- **Endpoint**: `/register`
- **Body**:
```json
{
  "email": "usuario@test.com",
  "password": "Password123!",
  "nombre": "Usuario Test",
  "idioma": "es"
}
```
- **Resultado**: Los tokens se guardan automáticamente en el environment

#### 1.2 Login (Si ya tienes cuenta)
- **Carpeta**: `1. Auth` → `Login`
- **Método**: POST
- **Endpoint**: `/login`
- **Body**:
```json
{
  "email": "usuario@test.com",
  "password": "Password123!"
}
```

---

### **Paso 2: Onboarding** 📝

#### 2.1 Guardar Información de Onboarding
- **Carpeta**: `2. Onboarding` → `POST - Guardar Onboarding`
- **Método**: POST
- **Endpoint**: `/onboarding`
- **Body**:
```json
{
  "area": "Desarrollo",
  "nivelExperiencia": "Junior",
  "nombreCargo": "Desarrollador Full Stack",
  "descripcionObjetivo": "Quiero trabajar en una startup de tecnología"
}
```

**Áreas disponibles:**
- `Desarrollo`
- `Analista TI`
- `Administracion`
- `Ingenieria Informatica`

**Niveles de experiencia:**
- `Junior` → se convierte a `jr`
- `Semi Senior` → se convierte a `mid`
- `Senior` → se convierte a `sr`

#### 2.2 Verificar Estado de Onboarding
- **Carpeta**: `2. Onboarding` → `GET - Ver Estado de Onboarding`
- **Método**: GET
- **Endpoint**: `/onboarding/status`

#### 2.3 Ver Información Guardada
- **Carpeta**: `2. Onboarding` → `GET - Obtener Información de Onboarding`
- **Método**: GET
- **Endpoint**: `/onboarding`

---

### **Paso 3: Test de Nivelación** 📊

#### 3.1 Iniciar Test
- **Carpeta**: `3. Tests de Nivelación` → `GET - Iniciar Test (Desarrollo)`
- **Método**: GET
- **Endpoint**: `/tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10`

**Parámetros disponibles:**
- `habilidad`: Desarrollo | Analista TI | Administracion | Ingenieria Informatica
- `cantidad`: Número de preguntas (default: 10)

**Respuesta esperada:**
```json
{
  "habilidad": "Desarrollo",
  "preguntas": [
    {
      "id": "uuid-pregunta-1",
      "enunciado": "¿Qué es Git?",
      "opciones": ["Sistema de control de versiones", "Base de datos", "Lenguaje de programación"],
      "dificultad": 1
    },
    ...
  ],
  "totalPreguntas": 10
}
```

**IMPORTANTE**: Copia los IDs de las preguntas para el siguiente paso.

#### 3.2 Responder y Evaluar Test
- **Carpeta**: `3. Tests de Nivelación` → `POST - Evaluar Test`
- **Método**: POST
- **Endpoint**: `/tests/nivelacion/evaluar`
- **Body**:
```json
{
  "habilidad": "Desarrollo",
  "respuestas": [
    {
      "preguntaId": "UUID_DE_PREGUNTA_1",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "UUID_DE_PREGUNTA_2",
      "respuestaSeleccionada": 1
    }
  ]
}
```

**Índices de respuesta:**
- `0` = Primera opción (A)
- `1` = Segunda opción (B)
- `2` = Tercera opción (C)
- `3` = Cuarta opción (D)

**Resultado esperado:**
```json
{
  "testId": "uuid-del-test",
  "habilidad": "Desarrollo",
  "puntaje": 80,
  "totalPreguntas": 10,
  "preguntasCorrectas": 8,
  "nivelSugerido": "avanzado",
  "feedback": "¡Excelente trabajo!...",
  "detalleRespuestas": [...]
}
```

**El `testId` se guarda automáticamente en el environment.**

#### 3.3 Ver Historial de Tests
- **Carpeta**: `3. Tests de Nivelación` → `GET - Ver Historial de Tests`
- **Método**: GET
- **Endpoint**: `/tests/nivelacion/historial`

#### 3.4 Ver Detalle de un Test
- **Carpeta**: `3. Tests de Nivelación` → `GET - Ver Detalle de Test`
- **Método**: GET
- **Endpoint**: `/tests/nivelacion/{{test_id}}`
- (Usa automáticamente el testId guardado)

---

### **Paso 4: Plan de Práctica** 📚

#### 4.1 Generar Plan desde Test
- **Carpeta**: `4. Plan de Práctica` → `POST - Generar Plan desde Test`
- **Método**: POST
- **Endpoint**: `/plan-practica/generar-desde-test`
- **Body**:
```json
{
  "testNivelacionId": "{{test_id}}"
}
```
(El `{{test_id}}` se reemplaza automáticamente con el valor guardado)

**Resultado esperado:**
```json
{
  "id": "uuid-del-plan",
  "area": "Desarrollo",
  "metaCargo": "Desarrollador Full Stack",
  "nivel": "jr",
  "pasos": [
    {
      "id": "uuid-paso-1",
      "orden": 1,
      "titulo": "Fundamentos de Desarrollo Web",
      "descripcion": "...",
      "sesionesPorSemana": 3
    },
    ...
  ]
}
```

**Pasos generados según nivel:**
- **Básico (jr)**: 4 pasos
- **Intermedio (mid)**: 5 pasos
- **Avanzado (sr)**: 6 pasos

#### 4.2 Ver Plan Actual
- **Carpeta**: `4. Plan de Práctica` → `GET - Ver Plan Actual`
- **Método**: GET
- **Endpoint**: `/plan-practica`

---

### **Paso 5: Perfil y Estadísticas** 👤

#### 5.1 Ver Mi Perfil
- **Carpeta**: `5. Perfil de Usuario` → `GET - Ver Mi Perfil`
- **Método**: GET
- **Endpoint**: `/me`

#### 5.2 Ver Perfil Detallado
- **Carpeta**: `5. Perfil de Usuario` → `GET - Ver Mi Perfil Detallado`
- **Método**: GET
- **Endpoint**: `/me/perfil`

#### 5.3 Ver Historial Completo
- **Carpeta**: `6. Historial Unificado` → `GET - Historial Completo`
- **Método**: GET
- **Endpoint**: `/historial`

---

## 🎯 Ejemplos de Casos de Uso

### Caso 1: Usuario Nuevo - Flujo Completo

1. **Register** → Guardar tokens
2. **POST /onboarding** → Área: "Desarrollo", Nivel: "Junior"
3. **GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10**
4. **POST /tests/nivelacion/evaluar** → Responder preguntas
5. **POST /plan-practica/generar-desde-test** → Usar testId
6. **GET /plan-practica** → Ver plan generado

### Caso 2: Usuario Existente - Ver Progreso

1. **Login** → Obtener tokens
2. **GET /onboarding/status** → Verificar onboarding
3. **GET /tests/nivelacion/historial** → Ver tests realizados
4. **GET /plan-practica** → Ver plan actual
5. **GET /historial** → Ver todo el historial

### Caso 3: Realizar Test en Otra Área

1. **GET /tests/nivelacion/iniciar?habilidad=Analista TI&cantidad=10**
2. **POST /tests/nivelacion/evaluar** → Evaluar
3. **POST /plan-practica/generar-desde-test** → Generar nuevo plan

---

## 🔍 Variables de Entorno

La colección usa las siguientes variables que se gestionan automáticamente:

| Variable | Descripción | Se guarda automáticamente |
|----------|-------------|---------------------------|
| `base_url` | URL del servidor | Manual (default: http://localhost:8080) |
| `access_token` | Token JWT de acceso | ✅ Sí (al hacer login/register) |
| `refresh_token` | Token de renovación | ✅ Sí (al hacer login/register) |
| `test_id` | ID del último test evaluado | ✅ Sí (al evaluar test) |
| `plan_id` | ID del plan generado | ✅ Sí (al generar plan) |
| `test_preguntas` | JSON con preguntas del test | ✅ Sí (al iniciar test) |

---

## ⚠️ Troubleshooting

### Error: "No hay suficientes preguntas disponibles"

**Causa**: No se han ejecutado las migraciones SQL.

**Solución**:
```sql
-- Conectar a PostgreSQL
psql -U postgres -d entrevista_app

-- Ejecutar migraciones
\i migrations/006_migrate_preguntas_to_nivelacion.sql
\i migrations/007_insert_preguntas_nivelacion_completas.sql
\i migrations/008_add_puntaje_recomendaciones_to_intento_prueba.sql
\i migrations/009_fix_tipo_pregunta_nulls.sql
```

### Error: 401 Unauthorized

**Causa**: Token expirado o inválido.

**Solución**:
1. Hacer login nuevamente
2. O usar el endpoint `POST /refresh` con el refresh_token

### Error: "PLAN_NOT_FOUND"

**Causa**: No se ha generado un plan todavía.

**Solución**:
1. Primero completar un test de nivelación
2. Luego generar el plan con `POST /plan-practica/generar-desde-test`

---

## 📊 Criterios de Evaluación

### Niveles según puntaje:

| Puntaje | Nivel | Descripción |
|---------|-------|-------------|
| ≥ 80% | Avanzado (3 / sr) | Dominio completo del área |
| ≥ 60% | Intermedio (2 / mid) | Conocimientos sólidos |
| < 60% | Básico (1 / jr) | Fundamentos por reforzar |

### Distribución de Preguntas (Balanceada):

- 40% Preguntas Básicas
- 40% Preguntas Intermedias
- 20% Preguntas Avanzadas

---

## 🎨 Tips de Uso en Postman

### 1. Ver Scripts de Tests

Algunos requests tienen scripts que guardan datos automáticamente:
- Click derecho en el request → `Edit`
- Tab `Tests` → Ver el código JavaScript

### 2. Ver Variables Guardadas

- Click en el ícono del ojo 👁️ (esquina superior derecha)
- Ver variables del environment actual

### 3. Copiar como cURL

- Click en `Code` (dentro del request)
- Seleccionar `cURL`
- Copiar para usar en terminal

### 4. Crear Nueva Request

- Click derecho en carpeta → `Add Request`
- Configurar método, URL, body

---

## 📞 Soporte

Si encuentras problemas:

1. Verificar que el servidor esté corriendo (`GET /health`)
2. Verificar que las migraciones SQL estén ejecutadas
3. Revisar los logs del servidor
4. Verificar que el token no haya expirado (15 minutos)

---

**Fecha de creación**: Enero 2025
**Versión de la API**: 1.0.0
**Base URL**: http://localhost:8080
