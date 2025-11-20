# Política de Borrado de Cuenta

## Resumen
Esta política describe cómo se procesa la eliminación de cuentas de usuario en cumplimiento con GDPR (Reglamento General de Protección de Datos) y LOPD (Ley Orgánica de Protección de Datos).

## Datos que se eliminan permanentemente

Al borrar una cuenta de usuario, se eliminan **de forma irreversible** los siguientes datos personales:

- **Información de identidad**: nombre, correo electrónico, contraseña hash
- **Perfil de usuario**: nivel de experiencia, área, preferencias, país
- **Historial de prácticas**: sesiones de entrevista, respuestas y retroalimentación
- **Resultados de tests**: intentos de prueba, respuestas y puntajes
- **Objetivos de carrera**: cargos y sectores objetivo
- **Planes de práctica**: planes personalizados y pasos asociados
- **Tokens de autenticación**: refresh tokens y tokens de recuperación de contraseña
- **Cuentas OAuth**: vinculaciones con Google y otros proveedores
- **Consentimientos**: registros de consentimientos otorgados
- **Suscripciones**: planes activos, histórico de pagos
- **Tickets de soporte**: consultas y respuestas del sistema de soporte
- **Membresías institucionales**: asignaciones de licencias y roles
- **Cache offline**: datos sincronizados en dispositivos

## Datos que se anonimizan

Para mantener la integridad de estadísticas agregadas y análisis de uso, algunos datos se anonimizan en lugar de eliminarse:

- **Logs de auditoría**: Los registros en `log_auditoria` establecen `usuario_id` a NULL (ON DELETE SET NULL), manteniendo el evento pero eliminando la relación con el usuario

## Tiempo de procesamiento

- **Ejecución**: El borrado es **inmediato** al confirmar la acción
- **Irreversibilidad**: El proceso es **permanente** y no puede deshacerse
- **Cascada**: Todos los datos relacionados se eliminan automáticamente mediante ON DELETE CASCADE en la base de datos

## Procedimiento técnico

1. El usuario autenticado envía una solicitud DELETE a `/cuenta` con confirmación explícita
2. El sistema valida la identidad del usuario mediante JWT
3. Se ejecuta la eliminación del registro en `app.usuario`
4. PostgreSQL elimina automáticamente todos los registros relacionados por foreign keys con ON DELETE CASCADE
5. Se invalidan todos los tokens de sesión activos
6. Se retorna confirmación HTTP 200 OK

## Derechos del usuario (GDPR/LOPD)

Este proceso implementa el **derecho al olvido** (Art. 17 GDPR) permitiendo a los usuarios:

- Solicitar la eliminación de todos sus datos personales
- Recibir confirmación inmediata del borrado
- Tener garantía de que los datos no pueden recuperarse

## Excepciones

No se eliminan:

- Registros necesarios para cumplir obligaciones legales (conservados de forma anónima)
- Datos agregados y estadísticas sin identificación personal

## Contacto

Para consultas sobre esta política: soporte@entrevistaapp.com
