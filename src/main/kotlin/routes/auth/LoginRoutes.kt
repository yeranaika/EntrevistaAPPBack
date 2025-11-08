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

            val user = AuthDeps.users.findByEmail(email)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))

            if (!verifyPassword(req.password, user.hash)) {
                return@post call.respond(HttpStatusCode.Unauthorized, ErrorRes("bad_credentials"))
            }

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
