# 🔧 Guía de Troubleshooting - Postman

## Problemas Comunes y Soluciones

### 1. ❌ "Could not get any response" o "Error: connect ECONNREFUSED"

**Causa**: El servidor no está corriendo

**Solución**:
```bash
# Verificar si el servidor está corriendo
curl http://localhost:8080/health

# Si no responde, iniciar el servidor
cd EntrevistaAPPBack
.\gradlew run
```

---

### 2. ❌ Variables no se guardan automáticamente

**Causa**: El environment no está seleccionado

**Solución**:
1. En Postman, arriba a la derecha, busca el dropdown de "Environments"
2. Selecciona **"EntrevistaAPP - Local"**
3. Verifica que aparezca seleccionado (con un ✓)

**Verificar variables**:
- Click en el ícono del ojo 👁️ (arriba derecha)
- Deberías ver: `base_url`, `access_token`, `refresh_token`, etc.

---

### 3. ❌ "401 Unauthorized" en endpoints protegidos

**Causa**: No tienes un token válido

**Solución**:
1. Ejecuta primero **"1. Auth → Register"** o **"Login"**
2. Verifica en la consola de Postman (abajo) que diga: "Tokens guardados exitosamente"
3. Verifica el environment (ícono del ojo 👁️) que `access_token` tenga un valor

**Si el token expiró**:
- Ejecuta **"1. Auth → Refresh Token"**
- O vuelve a hacer Login

---

### 4. ❌ "404 Not Found" en todos los endpoints

**Causa**: La URL base está mal configurada

**Solución**:
1. Verifica el environment: `base_url` debe ser `http://localhost:8080`
2. NO debe tener `/` al final
3. Verifica que el servidor esté en el puerto 8080

**Verificar puerto del servidor**:
```bash
# Ver application.yaml
cat src/main/resources/application.yaml
```

Busca:
```yaml
ktor:
  deployment:
    port: 8080
```

---

### 5. ❌ "Cannot read property 'accessToken' of undefined"

**Causa**: El script de test está intentando leer una respuesta que no existe

**Solución**:
1. Abre la **Console** en Postman (View → Show Postman Console)
2. Ejecuta el request de nuevo
3. Verifica la respuesta real del servidor

**Respuesta esperada de Login/Register**:
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "usuario": {
    "id": "uuid",
    "email": "test@test.com",
    "nombre": "Test"
  }
}
```

---

### 6. ❌ "Error: Invalid JSON"

**Causa**: El body del request tiene formato incorrecto

**Solución**:
1. Verifica que el body esté en modo **"raw"**
2. Selecciona **"JSON"** en el dropdown (no "Text")
3. Verifica que el JSON sea válido (sin comas extras, comillas correctas)

**Ejemplo correcto**:
```json
{
  "email": "test@test.com",
  "password": "Password123!"
}
```

---

### 7. ❌ "Test ID not found" al generar plan

**Causa**: La variable `test_id` no se guardó correctamente

**Solución**:
1. Ejecuta **"3. Tests de Nivelación → POST - Evaluar Test"**
2. Verifica en la Console que diga: "Test ID guardado: [uuid]"
3. Verifica el environment que `test_id` tenga un valor UUID

**Alternativa manual**:
1. Copia el `testId` de la respuesta de "Evaluar Test"
2. En el environment, pega el valor en `test_id`
3. Ejecuta "Generar Plan desde Test"

---

### 8. ❌ "REEMPLAZAR_CON_ID_PREGUNTA" en Evaluar Test

**Causa**: Necesitas copiar los IDs reales de las preguntas

**Solución**:
1. Ejecuta **"GET - Iniciar Test (Desarrollo)"**
2. Copia la respuesta completa
3. Extrae los `id` de cada pregunta
4. Reemplaza en el body de "Evaluar Test"

**Ejemplo**:

**Respuesta de Iniciar Test**:
```json
{
  "preguntas": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "pregunta": "¿Qué es REST?",
      "opciones": ["A", "B", "C", "D"]
    },
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "pregunta": "¿Qué es JSON?",
      "opciones": ["A", "B", "C", "D"]
    }
  ]
}
```

**Body de Evaluar Test**:
```json
{
  "habilidad": "Desarrollo",
  "respuestas": [
    {
      "preguntaId": "550e8400-e29b-41d4-a716-446655440000",
      "respuestaSeleccionada": 0
    },
    {
      "preguntaId": "660e8400-e29b-41d4-a716-446655440001",
      "respuestaSeleccionada": 2
    }
  ]
}
```

---

### 9. ❌ "Email already in use"

**Causa**: Ya existe un usuario con ese email

**Solución**:
- Cambia el email en el body de Register
- O usa Login en lugar de Register
- O elimina el usuario de la base de datos

**Cambiar email**:
```json
{
  "email": "usuario2@test.com",
  "password": "Password123!",
  "nombre": "Usuario Test 2",
  "idioma": "es"
}
```

---

### 10. ❌ Scripts no se ejecutan automáticamente

**Causa**: Los scripts están deshabilitados en Postman

**Solución**:
1. Settings (⚙️) → General
2. Busca "Script execution"
3. Asegúrate que esté **habilitado**

---

## 🧪 Test Rápido - Verificar que Todo Funciona

### Paso 1: Health Check
```bash
curl http://localhost:8080/health
```
**Esperado**: `OK`

### Paso 2: Register (en Postman)
- Endpoint: `POST {{base_url}}/auth/register`
- Body:
```json
{
  "email": "test@test.com",
  "password": "Password123!",
  "nombre": "Test User",
  "idioma": "es"
}
```
- **Esperado**: Status 200, tokens guardados

### Paso 3: Verificar Token
- Click en el ícono del ojo 👁️
- **Esperado**: `access_token` tiene un valor largo (JWT)

### Paso 4: Onboarding
- Endpoint: `POST {{base_url}}/onboarding`
- **Esperado**: Status 200, onboarding guardado

### Paso 5: Iniciar Test
- Endpoint: `GET {{base_url}}/tests/nivelacion/iniciar?habilidad=Desarrollo&cantidad=10`
- **Esperado**: Status 200, 10 preguntas

---

## 📞 ¿Aún no funciona?

### Información para diagnosticar:

1. **¿Qué error específico ves?**
   - Copia el mensaje de error completo
   - Captura de pantalla si es posible

2. **¿En qué endpoint falla?**
   - Nombre del request
   - Método (GET/POST)
   - URL completa

3. **¿Qué respuesta obtienes?**
   - Status code (200, 401, 404, 500, etc.)
   - Body de la respuesta
   - Headers

4. **Verifica la Console de Postman**:
   - View → Show Postman Console
   - Ejecuta el request
   - Copia todo el log

5. **Verifica el servidor**:
   - ¿Está corriendo?
   - ¿Hay errores en la consola del servidor?
   - ¿Qué puerto usa?

---

## 🎯 Checklist de Verificación

- [ ] Servidor corriendo (`.\gradlew run`)
- [ ] Health check responde (`curl http://localhost:8080/health`)
- [ ] Environment "EntrevistaAPP - Local" seleccionado
- [ ] Variable `base_url` = `http://localhost:8080`
- [ ] Scripts habilitados en Settings
- [ ] Postman Console abierta para ver logs
- [ ] Token obtenido con Register/Login
- [ ] Token visible en environment (ícono del ojo 👁️)

---

## 🔍 Comandos Útiles para Debugging

### Verificar servidor
```bash
curl http://localhost:8080/health
```

### Test manual de Register
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@test.com\",\"password\":\"Pass123!\",\"nombre\":\"Test\",\"idioma\":\"es\"}"
```

### Test manual de Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"test@test.com\",\"password\":\"Pass123!\"}"
```

### Ver logs del servidor
```bash
# En la terminal donde corre .\gradlew run
# Busca errores o warnings
```

---

## 💡 Tips Adicionales

1. **Usa la Console de Postman**: Es tu mejor amiga para debugging
2. **Verifica el environment**: Siempre que algo falle
3. **Copia los IDs**: No intentes escribirlos manualmente
4. **Usa variables**: `{{access_token}}`, `{{test_id}}`, etc.
5. **Ejecuta en orden**: Auth → Onboarding → Tests → Plan
6. **Guarda la colección**: Después de hacer cambios

---

## 🚀 Flujo Recomendado (Sin Errores)

```
1. .\gradlew run                          → Iniciar servidor
2. Seleccionar environment                → "EntrevistaAPP - Local"
3. Auth → Register                        → Crear usuario + tokens
4. Onboarding → POST Onboarding           → Configurar perfil
5. Tests → Iniciar Test (Desarrollo)      → Obtener preguntas
6. Tests → Evaluar Test                   → Enviar respuestas + guardar test_id
7. Plan → Generar Plan desde Test         → Crear plan personalizado
8. Plan → Ver Plan Actual                 → Ver pasos del plan
```

**Tiempo estimado**: 2-3 minutos

---

¿Cuál es el error específico que estás viendo? 🤔
