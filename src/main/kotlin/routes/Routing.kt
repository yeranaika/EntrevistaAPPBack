package routes

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import com.auth0.jwt.algorithms.Algorithm
import plugins.settings
import security.AuthCtx
import security.AuthCtxKey

fun Application.configureRouting() {
    routing {
        get("/health") { call.respondText("OK") }

        val ctx = if (attributes.contains(AuthCtxKey)) {
            attributes[AuthCtxKey]
        } else {
            // Fallback por si configureSecurity() aún no puso el atributo
            val s = settings()
            AuthCtx(s.jwtIssuer, s.jwtAudience, Algorithm.HMAC512(s.jwtSecret))
        }

        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)

        authenticate("auth-jwt") {
            get("/me") {
                val sub = call.principal<JWTPrincipal>()!!.subject
                call.respond(mapOf("userId" to sub))
            }
        }
    }
}
