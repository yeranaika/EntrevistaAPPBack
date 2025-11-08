package routes

import data.repository.admin.PreguntaRepository
import data.repository.admin.AdminUserRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.ConsentimientoRepository 

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import routes.auth.authRoutes
import routes.me.meRoutes
import routes.consent.ConsentRoutes
import routes.admin.AdminPreguntaCreateRoute
import routes.admin.AdminUserCreateRoutes
import com.example.routes.intentosRoutes  // ⬅️ AGREGAR ESTE IMPORT

import security.AuthCtx
import security.AuthCtxKey

// Recibimos los repos por parámetro para no crearlos aquí
fun Application.configureRouting(
    preguntaRepo: PreguntaRepository,
    adminUserRepo: AdminUserRepository
) {
    // Instancias de repos
    val users = UserRepository()
    val profiles = ProfileRepository()
    val consentRepo = ConsentimientoRepository() 
   
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

        // Consentimientos
        ConsentRoutes(consentRepo)
        
        // Intentos de prueba  ⬅️ AGREGAR ESTE COMENTARIO Y LA LÍNEA DE ABAJO
        intentosRoutes()

        // Admin: banco de preguntas
        AdminPreguntaCreateRoute(preguntaRepo)

        // Admin: crear usuarios (incluye admins)
        AdminUserCreateRoutes(adminUserRepo)
    }
}
