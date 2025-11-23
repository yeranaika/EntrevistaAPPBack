# 📮 Configuración de Postman - EntrevistaAPP

## 📦 Archivos Creados

He creado 4 archivos para facilitar las pruebas con Postman:

### 1. 📋 Colección de Postman
**Archivo**: `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`

Incluye **32 endpoints** organizados en 7 carpetas:
- ✅ **1. Auth** (3 endpoints): Register, Login, Refresh Token
- ✅ **2. Onboarding** (3 endpoints): Guardar, Ver Estado, Obtener Info
- ✅ **3. Tests de Nivelación** (7 endpoints): Iniciar, Evaluar, Historial
- ✅ **4. Plan de Práctica** (2 endpoints): Generar, Ver Plan
- ✅ **5. Perfil de Usuario** (2 endpoints): Ver Perfil, Perfil Detallado
- ✅ **6. Historial Unificado** (1 endpoint): Historial Completo
- ✅ **7. Health Check** (1 endpoint): Verificar Servidor

### 2. 🌍 Environment de Postman
**Archivo**: `EntrevistaAPP.postman_environment.json`

Variables configuradas automáticamente:
- `base_url` → http://localhost:8080
- `access_token` → Se guarda al hacer login/register
- `refresh_token` → Se guarda al hacer login/register
- `test_id` → Se guarda al evaluar un test
- `plan_id` → Se guarda al generar un plan
- `test_preguntas` → JSON con preguntas del test actual

### 3. 📖 Guía Completa
**Archivo**: `GUIA_POSTMAN.md`

Documentación completa con:
- ✅ Instrucciones de importación
- ✅ Flujo paso a paso de pruebas
- ✅ Ejemplos de requests y responses
- ✅ Troubleshooting
- ✅ Variables de entorno
- ✅ Criterios de evaluación

### 4. 📝 Ejemplos de Tests
**Archivo**: `EJEMPLOS_TESTS.md`

Ejemplos prácticos de:
- ✅ Tests para cada área (Desarrollo, Analista TI, etc.)
- ✅ Respuestas para diferentes niveles (Básico, Intermedio, Avanzado)
- ✅ Resultados esperados
- ✅ Scripts automáticos para Postman

---

## 🚀 Inicio Rápido

### Paso 1: Importar en Postman

1. **Abrir Postman**
2. **Import** (botón arriba a la izquierda)
3. Arrastrar estos 2 archivos:
   - ✅ `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`
   - ✅ `EntrevistaAPP.postman_environment.json`
4. **Seleccionar Environment**: "EntrevistaAPP - Local" (esquina superior derecha)

### Paso 2: Verificar Servidor

```bash
# Ejecutar servidor
./gradlew run

# En otra terminal, verificar
curl http://localhost:8080/health
# Debe responder: OK
```

### Paso 3: Probar Flujo Completo

#### 3.1 Autenticarse
```
POST /register
Body:
{
  "email": "test@example.com",
  "password": "Password123!",
  "nombre": "Usuario Test",
  "idioma": "es"
}
```
✅ Los tokens se guardan automáticamente

#### 3.2 Completar Onboarding
```
POST /onboarding
Body:
{
  "area": "Desarrollo",
  "nivelExperiencia": "Junior",
  "nombreCargo": "Desarrollador Full Stack",
  "descripcionObjetivo": "Trabajar en startups"
}
```

#### 3.3 Hacer Test de Nivelación
```
GET /tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10
```
✅ Guarda las preguntas automáticamente

```
POST /tests/nivelacion/evaluar
Body:
{
  "habilidad": "Desarrollo",
  "respuestas": [
    { "preguntaId": "uuid-1", "respuestaSeleccionada": 0 },
    { "preguntaId": "uuid-2", "respuestaSeleccionada": 1 },
    ...
  ]
}
```
✅ Guarda el testId automáticamente

#### 3.4 Generar Plan Personalizado
```
POST /plan-practica/generar-desde-test
Body:
{
  "testNivelacionId": "{{test_id}}"
}
```
✅ El `{{test_id}}` se reemplaza automáticamente

#### 3.5 Ver Plan Generado
```
GET /plan-practica
```

---

## 🎯 Características Especiales

### ✨ Scripts Automáticos

Varios endpoints incluyen scripts que:
- **Guardan tokens** automáticamente al hacer login/register
- **Guardan test_id** al evaluar un test
- **Guardan preguntas** al iniciar un test
- **Muestran resultados** en la consola de Postman

### 🔐 Autenticación Automática

Todos los endpoints protegidos usan:
```
Authorization: Bearer {{access_token}}
```
No necesitas copiar/pegar el token manualmente.

### 📊 Variables Dinámicas

La colección utiliza variables de entorno para:
- URLs base configurables
- IDs que se pasan entre requests
- Tokens de autenticación

---

## 📋 Checklist de Endpoints

### Autenticación
- [ ] `POST /register` - Crear usuario
- [ ] `POST /login` - Iniciar sesión
- [ ] `POST /refresh` - Renovar token

### Onboarding
- [ ] `POST /onboarding` - Guardar información
- [ ] `GET /onboarding/status` - Ver estado
- [ ] `GET /onboarding` - Obtener datos

### Tests de Nivelación
- [ ] `GET /tests/nivelacion/iniciar` - Obtener preguntas (4 variantes por área)
- [ ] `POST /tests/nivelacion/evaluar` - Evaluar respuestas
- [ ] `GET /tests/nivelacion/historial` - Ver historial completo
- [ ] `GET /tests/nivelacion/historial?habilidad=X` - Filtrar por área
- [ ] `GET /tests/nivelacion/{testId}` - Ver detalle de test

### Plan de Práctica
- [ ] `POST /plan-practica/generar-desde-test` - Generar plan
- [ ] `GET /plan-practica` - Ver plan actual

### Perfil
- [ ] `GET /me` - Ver mi perfil
- [ ] `GET /me/perfil` - Perfil detallado

### Historial
- [ ] `GET /historial` - Historial unificado

### Health
- [ ] `GET /health` - Verificar servidor

---

## 🔍 Troubleshooting

### ❌ Error: "No hay suficientes preguntas disponibles"

**Solución**: Ejecutar migraciones SQL
```sql
\i migrations/006_migrate_preguntas_to_nivelacion.sql
\i migrations/007_insert_preguntas_nivelacion_completas.sql
\i migrations/008_add_puntaje_recomendaciones_to_intento_prueba.sql
\i migrations/009_fix_tipo_pregunta_nulls.sql
```

### ❌ Error: 401 Unauthorized

**Solución**:
1. Hacer login nuevamente → `POST /login`
2. O renovar token → `POST /refresh`

### ❌ Error: "PLAN_NOT_FOUND"

**Solución**:
1. Primero hacer un test → `POST /tests/nivelacion/evaluar`
2. Luego generar plan → `POST /plan-practica/generar-desde-test`

### ❌ Variables no se guardan automáticamente

**Solución**:
1. Verificar que el Environment esté seleccionado (esquina superior derecha)
2. Ver scripts en la pestaña "Tests" de cada request
3. Revisar la consola de Postman (View → Show Postman Console)

---

## 📚 Documentación Relacionada

1. **GUIA_POSTMAN.md** - Guía detallada paso a paso
2. **EJEMPLOS_TESTS.md** - Ejemplos de tests y respuestas
3. **ONBOARDING_IMPLEMENTATION.md** - Documentación del sistema

---

## 🎓 Áreas y Niveles Disponibles

### Áreas (Habilidades):
- ✅ **Desarrollo** - 30 preguntas (10 por nivel)
- ✅ **Analista TI** - 30 preguntas (10 por nivel)
- ✅ **Administracion** - 30 preguntas (10 por nivel)
- ✅ **Ingenieria Informatica** - 30 preguntas (10 por nivel)

**Total**: 120 preguntas en el banco de datos

### Niveles de Evaluación:
| Puntaje | Nivel | Código | Pasos en Plan |
|---------|-------|--------|---------------|
| ≥ 80% | Avanzado | sr | 6 pasos |
| 60-79% | Intermedio | mid | 5 pasos |
| < 60% | Básico | jr | 4 pasos |

---

## 💡 Tips Avanzados

### 1. Ejecutar Colección Completa

Puedes ejecutar todos los tests en secuencia:
1. Click en la colección → "Run"
2. Seleccionar requests a ejecutar
3. Click en "Run EntrevistaAPP..."

### 2. Exportar Resultados

Para compartir resultados:
1. View → Show Postman Console
2. Copiar resultados
3. O exportar como archivo

### 3. Crear Nuevos Tests

Para agregar tus propios tests:
1. Click derecho en carpeta → "Add Request"
2. Configurar método, URL, headers
3. Agregar scripts si necesitas

### 4. Variables de Colección vs Environment

- **Environment**: Para datos que cambian (tokens, IDs)
- **Collection**: Para constantes (URLs, configuraciones fijas)

---

## 📊 Flujo Visual

```
┌─────────────────┐
│   1. Register   │
│   POST /register│
└────────┬────────┘
         │ Guarda tokens
         ▼
┌─────────────────┐
│  2. Onboarding  │
│ POST /onboarding│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  3. Iniciar Test│
│  GET .../iniciar│
└────────┬────────┘
         │ Guarda preguntas
         ▼
┌─────────────────┐
│ 4. Evaluar Test │
│ POST .../evaluar│
└────────┬────────┘
         │ Guarda testId
         ▼
┌─────────────────┐
│ 5. Generar Plan │
│ POST .../generar│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  6. Ver Plan    │
│ GET /plan-...   │
└─────────────────┘
```

---

## 🎉 ¡Listo para usar!

Ya tienes todo configurado para probar el sistema completo de:
- ✅ Autenticación
- ✅ Onboarding de usuarios
- ✅ Tests de nivelación con 120 preguntas
- ✅ Generación de planes personalizados
- ✅ Historial y estadísticas

**Tiempo estimado de configuración**: 5 minutos
**Endpoints disponibles**: 32
**Documentación**: 3 archivos guía

---

**Versión**: 1.0.0
**Fecha**: Enero 2025
**Servidor**: http://localhost:8080
**Framework**: Ktor + Kotlin + PostgreSQL
