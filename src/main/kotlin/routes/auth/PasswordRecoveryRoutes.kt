package routes.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import data.models.auth.ForgotPasswordReq
import data.models.auth.ForgotPasswordRes
import data.models.auth.ResetPasswordReq
import data.models.auth.ResetPasswordRes
import data.repository.usuarios.PasswordResetRepository
import data.tables.usuarios.UsuarioTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.Database
import services.EmailService

fun Route.passwordRecoveryRoutes(
    passwordResetRepo: PasswordResetRepository,
    emailService: EmailService,
    db: Database,
    oauthRepo: data.repository.usuarios.UsuariosOAuthRepository
) {
    // ============================
    // POST /auth/forgot-password
    // ============================
    post("/auth/forgot-password") {
        val body = runCatching { call.receive<ForgotPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

        val correo = body.correo.trim().lowercase()
        if (correo.isBlank() || !correo.contains("@")) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Correo electrónico inválido")
            )
        }

        try {
            // Crea registro solo si el usuario existe
            val resetInfo = passwordResetRepo.createForEmail(correo)

            // ❌ Si no hay usuario con ese correo
            if (resetInfo == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordRes(
                        message = "No existe ningún usuario registrado con ese correo"
                    )
                )
            }

            // ❌ Si el usuario está registrado con Google, no puede usar reset de contraseña
            if (oauthRepo.isGoogleUser(resetInfo.userId)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordRes(
                        message = "Esta cuenta fue creada con Google. Por favor, inicia sesión con Google."
                    )
                )
            }

            // Enviar el código por correo
            try {
                // Ajusta a la firma real de tu EmailService
                // Supongamos: fun sendRecoveryCode(toEmail: String, code: String)
                emailService.sendRecoveryCode(
                    resetInfo.code,
                    correo
                )

                call.application.environment.log.info(
                    "Código de recuperación enviado a: $correo"
                )
            } catch (emailEx: Exception) {
                call.application.environment.log.error(
                    "Error al enviar email a $correo: ${emailEx.message}"
                )
            }

            call.respond(
                HttpStatusCode.OK,
                ForgotPasswordRes(
                    message = "Te enviamos un código a tu correo"
                )
            )
        } catch (ex: Exception) {
            call.application.environment.log.error(
                "Error en forgot-password: ${ex.message}"
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }

    // ============================
    // POST /auth/reset-password
    // ============================
    post("/auth/reset-password") {
        val body = runCatching { call.receive<ResetPasswordReq>() }
            .getOrElse {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "JSON inválido")
                )
            }

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
            // Validar y consumir por correo + código
            val usuarioId = passwordResetRepo.consumeByEmail(correo, codigo)

            if (usuarioId == null) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResetPasswordRes(
                        message = "Código inválido o expirado"
                    )
                )
            }

            // ❌ Doble verificación: si el usuario está registrado con Google, no puede cambiar contraseña
            if (oauthRepo.isGoogleUser(usuarioId)) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ResetPasswordRes(
                        message = "Esta cuenta fue creada con Google. Por favor, inicia sesión con Google."
                    )
                )
            }

            // Hash de la nueva contraseña
            val hashedPassword = BCrypt
                .withDefaults()
                .hashToString(12, nuevaContrasena.toCharArray())

            // Actualizar contraseña en la tabla usuario
            val updated = newSuspendedTransaction(db = db) {
                UsuarioTable.update({ UsuarioTable.usuarioId eq usuarioId }) { st ->
                    st[UsuarioTable.contrasenaHash] = hashedPassword
                    // Si tienes columna updatedAt la pones aquí, si no, lo dejamos así
                    // st[UsuarioTable.updatedAt] = Instant.now()
                }
            }

            if (updated == 0) {
                return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al actualizar la contraseña")
                )
            }

            call.application.environment.log.info(
                "Contraseña actualizada para usuario: $correo"
            )

            call.respond(
                HttpStatusCode.OK,
                ResetPasswordRes(
                    message = "Contraseña actualizada exitosamente"
                )
            )
        } catch (ex: Exception) {
            call.application.environment.log.error(
                "Error en reset-password: ${ex.message}"
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error al procesar la solicitud")
            )
        }
    }
}
