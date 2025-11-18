package routes.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import data.models.auth.ForgotPasswordReq
import data.models.auth.ForgotPasswordRes
import data.models.auth.ResetPasswordReq
import data.models.auth.ResetPasswordRes
import data.repository.auth.RecoveryCodeRepository
import data.tables.usuarios.UsuarioTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import services.EmailService

fun Route.passwordRecoveryRoutes(
    recoveryCodeRepo: RecoveryCodeRepository,
    emailService: EmailService,
    db: org.jetbrains.exposed.sql.Database
) {
    // POST /auth/forgot-password
    post("/auth/forgot-password") {
        val body = runCatching { call.receive<ForgotPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

        // Validar formato de correo
        val correo = body.correo.trim().lowercase()
        if (correo.isBlank() || !correo.contains("@")) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Correo electrónico inválido")
            )
        }

        try {
            // Generar código (retorna null si el usuario no existe)
            val codigo = recoveryCodeRepo.createRecoveryCode(correo)

            // Si el código se generó exitosamente, enviar email
            if (codigo != null) {
                try {
                    // Obtener nombre del usuario para personalizar el email
                    val nombre = newSuspendedTransaction(db = db) {
                        UsuarioTable
                            .select(UsuarioTable.nombre)
                            .where { UsuarioTable.correo eq correo }
                            .limit(1)
                            .singleOrNull()
                            ?.get(UsuarioTable.nombre)
                    }

                    emailService.sendRecoveryCode(correo, codigo, nombre)
                    call.application.environment.log.info("Código de recuperación enviado a: $correo")
                } catch (emailEx: Exception) {
                    call.application.environment.log.error("Error al enviar email a $correo: ${emailEx.message}")
                    // No revelamos el error al cliente por seguridad
                }
            }

            // SIEMPRE respondemos 200 OK, incluso si el correo no existe
            // Esto previene ataques de enumeración de usuarios
            call.respond(
                HttpStatusCode.OK,
                ForgotPasswordRes(
                    message = "Si el correo existe, recibirás un código de recuperación en breve"
                )
            )
        } catch (ex: Exception) {
            call.application.environment.log.error("Error en forgot-password: ${ex.message}")
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }

    // POST /auth/reset-password
    post("/auth/reset-password") {
        val body = runCatching { call.receive<ResetPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

        // Validaciones
        val correo = body.correo.trim().lowercase()
        val codigo = body.codigo.trim()
        val nuevaContrasena = body.nuevaContrasena

        if (correo.isBlank() || !correo.contains("@")) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Correo electrónico inválido")
            )
        }

        if (codigo.length != 6 || !codigo.all { it.isDigit() }) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Código inválido")
            )
        }

        if (nuevaContrasena.length < 8) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "La contraseña debe tener al menos 8 caracteres")
            )
        }

        try {
            // Validar código
            val usuarioId = recoveryCodeRepo.validateCode(correo, codigo)

            if (usuarioId == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Código inválido o expirado")
                )
            }

            // Hash de la nueva contraseña
            val hashedPassword = BCrypt.withDefaults().hashToString(12, nuevaContrasena.toCharArray())

            // Actualizar contraseña en la base de datos
            val updated = newSuspendedTransaction(db = db) {
                UsuarioTable.update({ UsuarioTable.usuarioId eq usuarioId }) { st ->
                    st[UsuarioTable.contrasenaHash] = hashedPassword
                }
            }

            if (updated == 0) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al actualizar la contraseña")
                )
            }

            // Marcar código como usado
            recoveryCodeRepo.markCodeAsUsed(correo, codigo)

            call.application.environment.log.info("Contraseña actualizada para usuario: $correo")

            call.respond(
                HttpStatusCode.OK,
                ResetPasswordRes(message = "Contraseña actualizada exitosamente")
            )
        } catch (ex: Exception) {
            call.application.environment.log.error("Error en reset-password: ${ex.message}")
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }
}
