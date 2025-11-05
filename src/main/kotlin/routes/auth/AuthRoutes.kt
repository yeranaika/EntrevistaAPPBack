package routes.auth


import com.auth0.jwt.algorithms.Algorithm
import data.repository.PasswordResetRepository
import data.repository.RefreshTokenRepository
import data.repository.UserRepository
import data.repository.ProfileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
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

// ====== repos ======
private val users = UserRepository()
private val resets = PasswordResetRepository()
private val refreshRepo = RefreshTokenRepository()
private val profiles = ProfileRepository()

fun Route.authRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) = route("/auth") {

    // -------- Registro --------
    post("/register") {
        val req = runCatching { call.receive<RegisterReq>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        }

        val email = req.email.trim().lowercase()
        if (!email.contains("@") || req.password.length < 8) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("invalid_input"))
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
        if (listOf(req.nivelExperiencia, req.area, req.pais, req.notaObjetivos, req.flagsAccesibilidad)
                .any { it != null }) {
            profiles.create(
                userId = userId,
                nivelExperiencia = req.nivelExperiencia,
                area = req.area,
                pais = req.pais,
                notaObjetivos = req.notaObjetivos,
                flagsAccesibilidad = req.flagsAccesibilidad
            )
        }

        // Tokens
        val accessTtlSec = 15 * 60
        val access = issueAccessToken(userId.toString(), issuer, audience, algorithm, accessTtlSec)

        val refreshTtlMin = 60L * 24 * 15 // 15 días
        val refreshPlain = generateRefreshToken() // string opaco aleatorio
        val refreshHash  = hashRefreshToken(refreshPlain)
        val now = Instant.now()
        val exp = now.plus(refreshTtlMin, ChronoUnit.MINUTES)

        // Persistimos SOLO el hash
        refreshRepo.insert(
            userId = userId,
            tokenHash = refreshHash,
            issuedAt = now,
            expiresAt = exp
        )

        call.respond(HttpStatusCode.Created, LoginOk(accessToken = access, refreshToken = refreshPlain))
    }

    // -------- Login --------
    post("/login") {
        val req = runCatching { call.receive<LoginReq>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        }
        val email = req.email.trim().lowercase()
        val user = users.findByEmail(email)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))

        if (!verifyPassword(req.password, user.hash)) {
            return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))
        }

        val access = issueAccessToken(user.id.toString(), issuer, audience, algorithm, 15 * 60)

        val refreshPlain = generateRefreshToken()
        val refreshHash  = hashRefreshToken(refreshPlain)
        val now = Instant.now()
        val exp = now.plus(15, ChronoUnit.DAYS)

        refreshRepo.insert(
            userId = user.id,
            tokenHash = refreshHash,
            issuedAt = now,
            expiresAt = exp
        )

        call.respond(LoginOk(accessToken = access, refreshToken = refreshPlain))
    }

    // -------- Solicitar reset (devuelve code/token SOLO para dev) --------
    post("/request-reset") {
        val req = runCatching { call.receive<RequestResetReq>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        }
        val info = resets.createForEmail(req.email)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorRes("email_not_found"))

        call.respond(HttpStatusCode.Created, RequestResetOk(token = info.token.toString(), code = info.code))
    }

    // -------- Confirmar reset (consume token+code, setea nueva pass) --------
    post("/confirm-reset") {
        val req = runCatching { call.receive<ConfirmResetReq>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        }
        if (req.newPassword.length < 8) {
            return@post call.respond(HttpStatusCode.UnprocessableEntity, ErrorRes("weak_password"))
        }

        val userId = resets.consume(UUID.fromString(req.token), req.code)
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_or_expired"))

        users.updatePassword(userId, hashPassword(req.newPassword))
        call.respond(OkRes())
    }

    // -------- Rotación de refresh tokens --------
    post("/refresh") {
        val req = runCatching { call.receive<RefreshReq>() }.getOrElse {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        }
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
        val newHash  = hashRefreshToken(newPlain)
        val now = Instant.now()
        val exp = now.plus(15, ChronoUnit.DAYS)
        refreshRepo.insert(
            userId = found.userId,
            tokenHash = newHash,
            issuedAt = now,
            expiresAt = exp
        )

        val newAccess = issueAccessToken(found.userId.toString(), issuer, audience, algorithm, 15 * 60)
        call.respond(RefreshOk(accessToken = newAccess, refreshToken = newPlain))
    }
}
