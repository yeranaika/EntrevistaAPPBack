package routes

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import data.UserRepository
import security.hashPassword
import security.verifyPassword
import security.issueAccessToken
import java.util.*

@Serializable data class RegisterReq(val email: String, val password: String, val nombre: String? = null)
@Serializable data class LoginReq(val email: String, val password: String)

private val users = UserRepository()

fun Route.authRoutes(issuer: String, audience: String, algorithm: Algorithm) {

    route("/auth") {

        post("/register") {
            val req = runCatching { call.receive<RegisterReq>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_json"))
            }
            val email = req.email.trim().lowercase()
            if (!email.contains("@") || req.password.length < 8) {
                return@post call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to "invalid_input"))
            }

            if (users.existsByEmail(email)) {
                return@post call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_in_use"))
            }

            val hash = hashPassword(req.password)
            val id: UUID = users.create(email, hash, req.nombre)
            call.respond(HttpStatusCode.Created, mapOf("ok" to true, "userId" to id.toString()))
        }

        post("/login") {
            val req = runCatching { call.receive<LoginReq>() }.getOrElse {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_json"))
            }
            val email = req.email.trim().lowercase()
            val user = users.findByEmail(email)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "bad_credentials"))

                                if (!verifyPassword(req.password, user.hash)) {
                                    return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "bad_credentials"))
            }

            val access = issueAccessToken(user.id.toString(), issuer, audience, algorithm, ttlSeconds = 15 * 60)
            call.respond(mapOf("accessToken" to access))
        }
    }
}
