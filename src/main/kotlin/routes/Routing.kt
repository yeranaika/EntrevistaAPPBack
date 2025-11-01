package routes

import com.auth0.jwt.algorithms.Algorithm
import data.repository.ProfileRepository
import data.repository.UserRepository
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.auth.authRoutes
import routes.me.meRoutes
import security.AuthCtx
import security.AuthCtxKey

fun Application.configureRouting() {
    // Instancias de repos
    val users = UserRepository()
    val profiles = ProfileRepository()

    // El contexto JWT debe haber sido cargado por configureSecurity()
    val ctx: AuthCtx = if (attributes.contains(AuthCtxKey)) {
        attributes[AuthCtxKey]
    } else {
        // Si llegas aquí, aún no se ejecutó configureSecurity()
        throw IllegalStateException(
            "AuthCtx no disponible. Asegúrate de llamar primero a security.configureSecurity() en Application.module()."
        )
    }

    routing {
        // Healthcheck
        get("/health") { call.respondText("OK") }

        // Auth (register/login/refresh/reset)
        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)

        // /me y /me/perfil (GET/PUT)
        meRoutes(users, profiles)

        // No declares otro handler para /me aquí para evitar 405/404 por duplicados
    }
}
