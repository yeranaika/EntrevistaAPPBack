package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import data.repository.usuarios.PasswordResetRepository
import data.repository.usuarios.RefreshTokenRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.ProfileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ContentTransformationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import security.hashPassword
import security.verifyPassword
import security.issueAccessToken
import security.generateRefreshToken
import security.hashRefreshToken
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// ====== DTOs ======
@Serializable
data class RegisterReq(
    val email: String,
    val password: String,
    val nombre: String? = null,
    val idioma: String? = null,
    // Datos de perfil (opcionales)
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class LoginOk(val accessToken: String, val refreshToken: String? = null)
@Serializable data class ErrorRes(val error: String)

@Serializable data class RequestResetReq(val email: String)
@Serializable data class RequestResetOk(val ok: Boolean = true, val token: String, val code: String)
@Serializable data class ConfirmResetReq(val token: String, val code: String, val newPassword: String)
@Serializable data class OkRes(val ok: Boolean = true)

@Serializable data class RefreshReq(val refreshToken: String)
@Serializable data class RefreshOk(val accessToken: String, val refreshToken: String)

// ====== repos singletons ======
private val users = UserRepository()
private val resets = PasswordResetRepository()
private val refreshRepo = RefreshTokenRepository()
private val profiles = ProfileRepository()

// El mismo regex que tu CHECK de Postgres:
private val EMAIL_RE = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PAIS_RE  = Regex("^[A-Za-z]{2}$")

fun Route.authRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) = route("/auth") {

    // -------- Registro --------
    post("/register") {
        try {
            val req = call.receive<RegisterReq>()

            val email = req.email.trim().lowercase()
            if (!EMAIL_RE.matches(email)) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("invalid_email"))
            }
            if (req.password.length < 8) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("weak_password"))
            }
            // si viene perfil con país, valida formato de 2 letras
            if (!req.pais.isNullOrBlank() && !PAIS_RE.matches(req.pais)) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("invalid_country"))
            }
            if (users.existsByEmail(email)) {
                return@post call.respond(HttpStatusCode.Conflict, ErrorRes("email_in_use"))
            }

            val userId = users.create(
                email = email,
                hash = hashPassword(req.password),
                nombre = req.nombre,
                idioma = req.idioma
            )

            // Perfil opcional si se mandan campos
            val wantsProfile = sequenceOf(
                req.nivelExperiencia, req.area, req.pais, req.notaObjetivos, req.flagsAccesibilidad
            ).any { it != null }

            if (wantsProfile) {
                profiles.create(
                    userId = userId,
                    nivelExperiencia = req.nivelExperiencia,
                    area = req.area,
                    pais = req.pais,
                    notaObjetivos = req.notaObjetivos,
                    // Si tu tabla guarda TEXT/JSONB, en el repo conviene hacer flagsAccesibilidad?.toString()
                    flagsAccesibilidad = req.flagsAccesibilidad
                )
            }

            // Tokens
            val accessTtlSec = 15 * 60
            val access = issueAccessToken(userId.toString(), issuer, audience, algorithm, accessTtlSec)

            // Emitimos refresh y lo persistimos como hash
            val refreshPlain = generateRefreshToken()
            issueNewRefresh(refreshPlain, userId)

            call.respond(HttpStatusCode.Created, LoginOk(accessToken = access, refreshToken = refreshPlain))
        } catch (_: ContentTransformationException) {
            // Body no es JSON válido o faltan campos mínimos
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            application.log.error("Register failed", t) // ← ver consola para la causa real
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }

    // -------- Login --------
    post("/login") {
        try {
            val req = call.receive<LoginReq>()
            val email = req.email.trim().lowercase()
            val user = users.findByEmail(email)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))

            if (!verifyPassword(req.password, user.hash)) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))
            }

            val access = issueAccessToken(user.id.toString(), issuer, audience, algorithm, 15 * 60)

            val refreshPlain = generateRefreshToken()
            issueNewRefresh(refreshPlain, user.id)

            call.respond(LoginOk(accessToken = access, refreshToken = refreshPlain))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            application.log.error("Login failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }

    // -------- Solicitar reset (devuelve code/token SOLO para dev) --------
    post("/request-reset") {
        try {
            val req = call.receive<RequestResetReq>()
            val info = resets.createForEmail(req.email)
                ?: return@post call.respond(HttpStatusCode.NotFound, ErrorRes("email_not_found"))

            call.respond(HttpStatusCode.Created, RequestResetOk(token = info.token.toString(), code = info.code))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            application.log.error("Request-reset failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }

    // -------- Confirmar reset (consume token+code, setea nueva pass) --------
    post("/confirm-reset") {
        try {
            val req = call.receive<ConfirmResetReq>()
            if (req.newPassword.length < 8) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("weak_password"))
            }

            val userId = resets.consume(UUID.fromString(req.token), req.code)
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_or_expired"))

            users.updatePassword(userId, hashPassword(req.newPassword))
            call.respond(OkRes())
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            application.log.error("Confirm-reset failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }

    // -------- Rotación de refresh tokens --------
    post("/refresh") {
        try {
            val req = call.receive<RefreshReq>()
            val provided = req.refreshToken.trim()
            if (provided.isEmpty()) {
                return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("missing_refresh"))
            }

            val hash = hashRefreshToken(provided)
            val found = refreshRepo.findActiveByHash(hash)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("invalid_refresh"))

            // Rotamos: revocamos el anterior y emitimos uno nuevo
            refreshRepo.revoke(found.id)

            val newPlain = generateRefreshToken()
            issueNewRefresh(newPlain, found.userId)

            val newAccess = issueAccessToken(found.userId.toString(), issuer, audience, algorithm, 15 * 60)
            call.respond(RefreshOk(accessToken = newAccess, refreshToken = newPlain))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            application.log.error("Refresh failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}

/** Helper local para persistir un refresh token nuevo (hash + expiración) */
private suspend fun issueNewRefresh(plain: String, userId: UUID) {
    val now = Instant.now()
    val exp = now.plus(15, ChronoUnit.DAYS)
    refreshRepo.insert(
        userId = userId,
        tokenHash = hashRefreshToken(plain),
        issuedAt = now,
        expiresAt = exp
    )
}
