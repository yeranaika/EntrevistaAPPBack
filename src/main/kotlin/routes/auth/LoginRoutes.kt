package routes.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.issueAccessToken
import security.generateRefreshToken

// OJO: aquí asumo que ya tienes estos data class definidos en este package:
// data class LoginReq(val email: String, val password: String)
// data class LoginOk(val accessToken: String, val refreshToken: String)
// data class ErrorRes(val error: String)

fun Route.loginRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    post("/login") {
        try {
            val req = call.receive<LoginReq>()

            val email = req.email.trim().lowercase()
            val password = req.password        // 👈 NO lo toques (ni lowercase, ni trim raro)

            val log = call.application.environment.log
            log.info("Login attempt for email: $email")

            // 1) Buscar usuario por correo
            val user = AuthDeps.users.findByEmail(email)
            if (user == null) {
                log.warn("User not found for email: $email")
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRes("bad_credentials")
                )
            }

            log.info("User found: ${user.id}, checking password...")

            // 2) Verificar contraseña con BCrypt directamente
            val verified = BCrypt.verifyer()
                .verify(password.toCharArray(), user.hash.toCharArray())
                .verified

            if (!verified) {
                log.warn("Password verification failed for user: ${user.id}")
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorRes("bad_credentials")
                )
            }

            log.info("Password verified successfully for user: ${user.id}")

            // 3) Generar access token
            val access = issueAccessToken(
                subject = user.id.toString(),
                issuer = issuer,
                audience = audience,
                algorithm = algorithm,
                ttlSeconds = 15 * 60,
                extraClaims = mapOf("role" to user.rol) // claim "role" como antes
            )

            // 4) Generar refresh token y guardarlo
            val refreshPlain = generateRefreshToken()
            issueNewRefresh(AuthDeps.refreshRepo, refreshPlain, user.id)

            // 5) Responder igual que siempre
            call.respond(
                LoginOk(
                    accessToken = access,
                    refreshToken = refreshPlain
                )
            )
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Login failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
