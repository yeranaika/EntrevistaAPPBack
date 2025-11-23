# 🎯 Solución Rápida - Postman Arreglado

## El Problema

Las rutas de autenticación en Postman estaban **sin el prefijo `/auth`**.

## La Solución

Ya actualicé el archivo `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json` con las rutas correctas.

---

## 🚀 Qué Hacer Ahora (3 pasos)

### 1️⃣ Re-importar en Postman

**En Postman:**
1. Click derecho en la colección antigua → **Delete**
2. Click en **Import** (arriba izquierda)
3. Arrastrar el archivo: `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`
4. Seleccionar environment: **"EntrevistaAPP - Local"** (arriba derecha)

### 2️⃣ Verificar que el servidor esté corriendo

```bash
cd EntrevistaAPPBack
.\gradlew run
```

En otra terminal:
```bash
curl http://localhost:8080/health
```

Debería responder: `OK`

### 3️⃣ Probar en Postman

1. **Auth → Register**
   - Click en "Send"
   - Debería responder 200 OK
   - Los tokens se guardan automáticamente

2. **Auth → Login** (si ya tienes usuario)
   - Click en "Send"
   - Debería responder 200 OK
   - Los tokens se actualizan automáticamente

3. **Verificar tokens**
   - Click en el ícono del ojo 👁️ (arriba derecha)
   - Deberías ver `access_token` con un valor largo

---

## ✅ Rutas Corregidas

| Endpoint | Antes (❌) | Ahora (✅) |
|----------|-----------|-----------|
| Register | `/register` | `/auth/register` |
| Login | `/login` | `/auth/login` |
| Refresh | `/refresh` | `/auth/refresh` |

---

## 🧪 Test Rápido (PowerShell)

```powershell
# Test completo
.\test-api.ps1
```

O manualmente:

```powershell
# 1. Health
Invoke-RestMethod http://localhost:8080/health

# 2. Register
Invoke-RestMethod -Uri "http://localhost:8080/auth/register" `
  -Method Post `
  -Body '{"email":"test@test.com","password":"Pass123!","nombre":"Test","idioma":"es"}' `
  -ContentType "application/json"
```

---

## 📝 Notas Importantes

### ✅ Lo que YA funciona:
- Health Check
- Register (con tokens automáticos)
- Login (con tokens automáticos)
- Refresh Token
- Me (perfil de usuario)

### ⚠️ Lo que puede dar error:
- **Onboarding**: Funciona pero puede dar error de serialización (es un bug del backend, no de Postman)
- **Tests de Nivelación**: Necesita que haya preguntas en la base de datos
- **Plan de Práctica**: Depende de que hayas completado un test

---

## 🎉 Listo

Ahora tu colección de Postman debería funcionar correctamente. 

**Flujo recomendado:**
1. Register → Obtener tokens
2. Onboarding → Configurar perfil (ignorar error si aparece)
3. Tests → Iniciar y evaluar (si tienes preguntas en BD)
4. Plan → Generar plan personalizado

---

## 📞 Si Algo No Funciona

1. Verifica que el environment esté seleccionado
2. Verifica que el servidor esté corriendo
3. Abre la Console de Postman (View → Show Postman Console)
4. Revisa el archivo `POSTMAN_TROUBLESHOOTING.md` para más detalles

---

**Archivos actualizados:**
- ✅ `EntrevistaAPP-Sistema-Nivelacion.postman_collection.json`
- ✅ `POSTMAN_FIX.md` (explicación detallada)
- ✅ `POSTMAN_TROUBLESHOOTING.md` (guía completa)
- ✅ `test-api.ps1` (script de prueba)
