# ✅ Implementación Completa: Recuperación de Contraseña por Email

## 📋 Resumen de la Implementación

Se ha implementado exitosamente un sistema completo de recuperación de contraseña mediante códigos enviados por email usando Gmail SMTP.

---

## 📁 Archivos Creados/Modificados

### 1. **Nuevas Tablas de Base de Datos**

#### `RecoveryCodeTable.kt`
- **Ubicación**: `src/main/kotlin/data/tables/auth/RecoveryCodeTable.kt`
- **Descripción**: Tabla para almacenar códigos de recuperación temporales
- **Campos**:
  - `id` (UUID) - Primary Key
  - `usuarioId` (UUID) - FK a usuario
  - `codigo` (VARCHAR 6) - Código de 6 dígitos
  - `fechaExpiracion` (TIMESTAMP) - Expira en 15 minutos
  - `usado` (BOOLEAN) - Marca si ya fue usado
  - `fechaCreacion` (TIMESTAMP) - Fecha de creación

### 2. **Nuevos Repositorios**

#### `RecoveryCodeRepository.kt`
- **Ubicación**: `src/main/kotlin/data/repository/auth/RecoveryCodeRepository.kt`
- **Métodos**:
  - `createRecoveryCode(correo: String): String?` - Genera y guarda un código
  - `validateCode(correo: String, codigo: String): UUID?` - Valida código y retorna userId
  - `markCodeAsUsed(correo: String, codigo: String): Boolean` - Marca código como usado
  - `cleanExpiredCodes(): Int` - Limpia códigos expirados (mantenimiento)

### 3. **Servicio de Email**

#### `EmailService.kt`
- **Ubicación**: `src/main/kotlin/services/EmailService.kt`
- **Descripción**: Servicio para envío de emails usando Gmail SMTP
- **Características**:
  - Template HTML profesional para emails
  - Configuración SMTP segura (TLS)
  - Soporte para Gmail App Passwords
  - Personalización con nombre del usuario

### 4. **Modelos de Datos**

#### `PasswordRecoveryModels.kt`
- **Ubicación**: `src/main/kotlin/data/models/auth/PasswordRecoveryModels.kt`
- **Modelos**:
  ```kotlin
  data class ForgotPasswordReq(val correo: String)
  data class ForgotPasswordRes(val message: String)
  data class ResetPasswordReq(
      val correo: String,
      val codigo: String,
      val nuevaContrasena: String
  )
  data class ResetPasswordRes(val message: String)
  ```

### 5. **Rutas de API**

#### `PasswordRecoveryRoutes.kt`
- **Ubicación**: `src/main/kotlin/routes/auth/PasswordRecoveryRoutes.kt`
- **Endpoints implementados**:

##### **POST /auth/forgot-password**
Solicita un código de recuperación
```json
Request:
{
  "correo": "usuario@email.com"
}

Response (200 OK):
{
  "message": "Si el correo existe, recibirás un código de recuperación en breve"
}
```

##### **POST /auth/reset-password**
Restablece la contraseña usando el código
```json
Request:
{
  "correo": "usuario@email.com",
  "codigo": "123456",
  "nuevaContrasena": "NuevaPassword123"
}

Response (200 OK):
{
  "message": "Contraseña actualizada exitosamente"
}

Errores posibles:
- 400: Código inválido o expirado
- 400: Correo inválido
- 400: Contraseña muy corta (mínimo 8 caracteres)
```

### 6. **Archivos Modificados**

#### `build.gradle.kts`
Agregadas dependencias:
```kotlin
// Email (Jakarta Mail para envío de correos)
implementation("com.sun.mail:jakarta.mail:2.0.1")
implementation("jakarta.activation:jakarta.activation-api:2.1.0")
```

#### `Application.kt`
- Inicialización de `RecoveryCodeRepository`
- Inicialización de `EmailService` con variables de entorno
- Integración con el routing

#### `Routing.kt`
- Agregada llamada a `passwordRecoveryRoutes()`

#### `Database.kt`
- Agregada `RecoveryCodeTable` a las migraciones automáticas

---

## ⚙️ Configuración de Variables de Entorno

### 1. Crear archivo `.env`

Crea un archivo `.env` en la raíz del proyecto backend:

```bash
# En: EntrevistaAPPBack/.env

# Database
DB_URL=jdbc:postgresql://localhost:5432/entrevista_db
DB_USER=postgres
DB_PASS=tu_password_postgres

# JWT
JWT_SECRET=tu-secret-jwt-super-secreto
JWT_ISSUER=https://localhost:8080
JWT_AUDIENCE=https://localhost:8080

# Google OAuth
GOOGLE_CLIENT_ID=tu-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=tu-client-secret

# ⭐ Email (Gmail SMTP) - REQUERIDO PARA RECUPERACIÓN DE CONTRASEÑA
GMAIL_USER=tu-email@gmail.com
GMAIL_APP_PASSWORD=abcdabcdabcdabcd  # 16 caracteres sin espacios

# SMTP (opcional - por defecto usa Gmail)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
```

### 2. Obtener Gmail App Password

#### ⚠️ IMPORTANTE: NO uses tu contraseña normal de Gmail

1. **Habilitar verificación en 2 pasos**:
   - Ve a: https://myaccount.google.com/security
   - Busca "Verificación en dos pasos" y actívala

2. **Generar App Password**:
   - Ve a: https://myaccount.google.com/apppasswords
   - Selecciona app: **"Correo"**
   - Selecciona dispositivo: **"Otro (nombre personalizado)"**
   - Escribe: **"EntrevistaAPP Backend"**
   - Clic en **"Generar"**
   - Copia la contraseña de 16 dígitos (SIN ESPACIOS)

3. **Configurar en .env**:
   ```bash
   GMAIL_USER=tu-email@gmail.com
   GMAIL_APP_PASSWORD=abcdabcdabcdabcd  # Los 16 caracteres sin espacios
   ```

### 3. Archivo `.env.example`

Ya se creó un archivo `.env.example` con la plantilla completa.

---

## 🔒 Características de Seguridad Implementadas

1. ✅ **Códigos de 6 dígitos aleatorios** (100,000 a 999,999)
2. ✅ **Expiración de 15 minutos** por código
3. ✅ **Un solo uso por código** (se marca como usado)
4. ✅ **Invalidación automática** de códigos anteriores al generar uno nuevo
5. ✅ **No revela si el correo existe** (previene enumeración de usuarios)
6. ✅ **Hash BCrypt** de contraseñas (factor 12)
7. ✅ **Validación de contraseña mínima** (8 caracteres)
8. ✅ **Conexión SMTP segura** (TLS 1.2)
9. ✅ **Logs de auditoría** de todas las operaciones

---

## 🧪 Cómo Probar

### 1. Iniciar el backend

```bash
cd EntrevistaAPPBack
.\gradlew.bat run
```

### 2. Probar endpoint forgot-password

```bash
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"correo":"tu-email@ejemplo.com"}'
```

**Respuesta esperada:**
```json
{
  "message": "Si el correo existe, recibirás un código de recuperación en breve"
}
```

**Revisa tu email** - Deberías recibir un correo con un código de 6 dígitos.

### 3. Probar endpoint reset-password

```bash
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{
    "correo":"tu-email@ejemplo.com",
    "codigo":"123456",
    "nuevaContrasena":"MiNuevaPassword123"
  }'
```

**Respuesta esperada:**
```json
{
  "message": "Contraseña actualizada exitosamente"
}
```

---

## 📧 Ejemplo de Email Enviado

El usuario recibirá un email HTML con:

- **Asunto**: "Código de recuperación - EntrevistaAPP"
- **Código destacado** en una caja grande
- **Tiempo de expiración** (15 minutos)
- **Advertencia** si no solicitó el cambio
- **Diseño profesional** con colores corporativos

---

## 📊 Estructura de la Base de Datos

La tabla se crea automáticamente en PostgreSQL:

```sql
CREATE TABLE app.recovery_code (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL REFERENCES app.usuario(usuario_id),
    codigo VARCHAR(6) NOT NULL,
    fecha_expiracion TIMESTAMP WITH TIME ZONE NOT NULL,
    usado BOOLEAN DEFAULT false NOT NULL,
    fecha_creacion TIMESTAMP WITH TIME ZONE NOT NULL
);
```

---

## 🛠️ Mantenimiento (Opcional)

### Limpieza automática de códigos expirados

Puedes agregar una tarea programada en `Application.kt`:

```kotlin
// En Application.module()
launch {
    while (true) {
        delay(3600000) // Cada hora
        try {
            val cleaned = recoveryCodeRepo.cleanExpiredCodes()
            log.info("Códigos expirados limpiados: $cleaned")
        } catch (e: Exception) {
            log.error("Error limpiando códigos expirados: ${e.message}")
        }
    }
}
```

---

## ✅ Checklist de Implementación

- [x] Dependencias de Jakarta Mail agregadas
- [x] Tabla RecoveryCodeTable creada
- [x] RecoveryCodeRepository implementado
- [x] EmailService con Gmail SMTP
- [x] Modelos de datos creados
- [x] Endpoint POST /auth/forgot-password
- [x] Endpoint POST /auth/reset-password
- [x] Integración con Application.kt
- [x] Integración con Routing.kt
- [x] Migraciones de base de datos
- [x] Template HTML de email
- [x] Documentación completa
- [x] Variables de entorno configuradas
- [x] Build exitoso

---

## 🎯 Siguientes Pasos

1. **Configurar Gmail App Password** siguiendo las instrucciones en `GMAIL_SETUP.md`
2. **Agregar .env a .gitignore** (si no está ya)
3. **Probar los endpoints** con un usuario real
4. **Personalizar el template de email** si lo deseas
5. **Agregar rate limiting** (opcional, para prevenir abuso)

---

## 📚 Documentación Adicional

- Ver: `GMAIL_SETUP.md` para instrucciones detalladas de Gmail
- Ver: `.env.example` para plantilla de configuración

---

## 🐛 Troubleshooting

### Error: "GMAIL_USER no configurado"
- Asegúrate de crear el archivo `.env` con las credenciales

### Error: "Authentication failed"
- Verifica que estés usando App Password, no tu contraseña normal
- Asegúrate de que la verificación en 2 pasos esté habilitada

### No recibo el email
- Revisa la carpeta de spam
- Verifica que GMAIL_USER sea tu email completo
- Revisa los logs del backend para ver si hay errores

### Código inválido o expirado
- Los códigos expiran en 15 minutos
- Cada código solo se puede usar una vez
- Solicita un nuevo código si es necesario

---

## 📞 Soporte

Si tienes problemas:
1. Revisa los logs del backend
2. Verifica las variables de entorno
3. Asegúrate de que la base de datos esté corriendo
4. Consulta `GMAIL_SETUP.md` para Gmail

---

**¡Implementación completada exitosamente! 🎉**
