# Configuración de Gmail para Recuperación de Contraseña

## Pasos para obtener la App Password de Gmail

### 1. Habilitar la verificación en 2 pasos

1. Ve a tu cuenta de Google: https://myaccount.google.com/
2. En el menú izquierdo, selecciona **"Seguridad"**
3. En la sección **"Cómo inicias sesión en Google"**, haz clic en **"Verificación en dos pasos"**
4. Sigue las instrucciones para configurar la verificación en dos pasos

### 2. Generar una App Password

1. Una vez habilitada la verificación en 2 pasos, ve a: https://myaccount.google.com/apppasswords
2. Si te pide iniciar sesión, ingresa tu contraseña de Gmail
3. En **"Seleccionar app"**, elige **"Correo"**
4. En **"Seleccionar dispositivo"**, elige **"Otro (nombre personalizado)"**
5. Escribe un nombre como: **"EntrevistaAPP Backend"**
6. Haz clic en **"Generar"**
7. Google te mostrará una contraseña de 16 dígitos (con espacios): **xxxx xxxx xxxx xxxx**
8. **¡IMPORTANTE!** Copia esta contraseña inmediatamente (sin espacios)

### 3. Configurar las variables de entorno

Crea o edita el archivo `.env` en la raíz del proyecto backend:

```bash
# En: EntrevistaAPPBack/.env

GMAIL_USER=tu-email@gmail.com
GMAIL_APP_PASSWORD=xxxxxxxxxxxxxxxx  # La contraseña de 16 dígitos SIN ESPACIOS
```

### 4. Ejemplo de archivo .env completo

```env
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

# Email (Gmail SMTP)
GMAIL_USER=tu-email@gmail.com
GMAIL_APP_PASSWORD=abcdabcdabcdabcd  # 16 caracteres sin espacios

# SMTP (opcional - por defecto usa Gmail)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
```

## ⚠️ Notas Importantes

1. **NO uses tu contraseña normal de Gmail** - Solo funciona con App Password
2. **NO compartas el archivo .env** - Agrégalo a `.gitignore`
3. **Guarda la App Password** - Google solo la muestra una vez
4. Si pierdes la App Password, puedes generar una nueva

## Verificar que funciona

Cuando inicies el backend, deberías ver en los logs:

```
✅ Email enviado exitosamente a: usuario@email.com
```

Si ves un error, revisa:
- Que la App Password esté correcta (16 caracteres sin espacios)
- Que GMAIL_USER sea tu email completo
- Que tengas internet
- Que la verificación en 2 pasos esté habilitada

## Endpoints disponibles

### POST /auth/forgot-password
Solicita un código de recuperación

```json
{
  "correo": "usuario@email.com"
}
```

**Respuesta:**
```json
{
  "message": "Si el correo existe, recibirás un código de recuperación en breve"
}
```

### POST /auth/reset-password
Restablece la contraseña usando el código

```json
{
  "correo": "usuario@email.com",
  "codigo": "123456",
  "nuevaContrasena": "NuevaPassword123"
}
```

**Respuesta:**
```json
{
  "message": "Contraseña actualizada exitosamente"
}
```

## Tabla en la base de datos

La tabla `app.recovery_code` se crea automáticamente con:

- `id` (UUID) - Primary Key
- `usuario_id` (UUID) - Foreign Key a usuario
- `codigo` (VARCHAR 6) - Código de 6 dígitos
- `fecha_expiracion` (TIMESTAMP) - Expira en 15 minutos
- `usado` (BOOLEAN) - true si ya fue usado
- `fecha_creacion` (TIMESTAMP) - Cuándo se creó

## Características de seguridad

1. **Los códigos expiran en 15 minutos**
2. **Cada código solo se puede usar una vez**
3. **Al solicitar un nuevo código, los anteriores se invalidan**
4. **No se revela si el correo existe o no** (previene enumeración de usuarios)
5. **La contraseña se hashea con BCrypt** antes de guardarla
6. **Se valida que la contraseña tenga mínimo 8 caracteres**

## Limpieza de códigos expirados (opcional)

Puedes crear una tarea programada para limpiar códigos viejos:

```kotlin
// En tu código, por ejemplo en Application.kt
launch {
    while (true) {
        delay(3600000) // Cada hora
        recoveryCodeRepo.cleanExpiredCodes()
    }
}
```
