package routes

import com.auth0.jwt.algorithms.Algorithm
import data.ProfileRepository
import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import security.hashPassword
import security.issueAccessToken
import security.verifyPassword
import java.util.*

@Serializable
data class RegisterReq(
    val email: String,
    val password: String,
    val nombre: String? = null,
    val idioma: String? = null,           // <- usuario
    // perfil:
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null,
    val flagsAccesibilidad: JsonElement? = null
)

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class ErrorRes(val error: String)
@Serializable data class RegisterOk(
    val ok: Boolean = true,
    val userId: String,
    val email: String,
    val nombre: String? = null,
    val idioma: String
)
@Serializable data class LoginOk(val accessToken: String)

private val users = UserRepository()
private val profiles = ProfileRepository()

fun Route.authRoutes(issuer: String, audience: String, algorithm: Algorithm) = route("/auth") {

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

        // Crear perfil sólo si llegó algún campo de perfil
        if (req.nivelExperiencia != null ||
            req.area != null ||
            req.pais != null ||
            req.notaObjetivos != null ||
            req.flagsAccesibilidad != null) {

            profiles.create(
                userId = userId,
                nivelExperiencia = req.nivelExperiencia,
                area = req.area,
                pais = req.pais,
                notaObjetivos = req.notaObjetivos,
                flagsAccesibilidad = req.flagsAccesibilidad
            )
        }

        call.respond(
            HttpStatusCode.Created,
            RegisterOk(
                userId = userId.toString(),
                email = email,
                nombre = req.nombre,
                idioma = req.idioma ?: "es"
            )
        )
    }

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
        call.respond(LoginOk(access))
    }
}
