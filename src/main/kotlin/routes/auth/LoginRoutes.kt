package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.issueAccessToken
import security.verifyPassword
import security.generateRefreshToken

fun Route.loginRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) {
    post("/login") {
        try {
            val req = call.receive<LoginReq>()
            val email = req.email.trim().lowercase()

            call.application.environment.log.info("Login attempt for email: $email")

            val user = AuthDeps.users.findByEmail(email)
            if (user == null) {
                call.application.environment.log.warn("User not found for email: $email")
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))
            }

            call.application.environment.log.info("User found: ${user.id}, checking password...")

            if (!verifyPassword(req.password, user.hash)) {
                call.application.environment.log.warn("Password verification failed for user: ${user.id}")
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))
            }

            call.application.environment.log.info("Password verified successfully for user: ${user.id}")

            val access = issueAccessToken(
                subject = user.id.toString(),
                issuer = issuer,
                audience = audience,
                algorithm = algorithm,
                ttlSeconds = 15 * 60,
                extraClaims = mapOf("role" to user.rol) // ← IMPORTANTE: claim 'role'
            )

            val refreshPlain = generateRefreshToken()
            issueNewRefresh(AuthDeps.refreshRepo, refreshPlain, user.id)

            call.respond(LoginOk(accessToken = access, refreshToken = refreshPlain))
        } catch (_: ContentTransformationException) {
            call.respond(HttpStatusCode.BadRequest, ErrorRes("invalid_json"))
        } catch (t: Throwable) {
            call.application.environment.log.error("Login failed", t)
            call.respond(HttpStatusCode.InternalServerError, ErrorRes("server_error"))
        }
    }
}
