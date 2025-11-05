// src/main/kotlin/routes/Routing.kt
package routes

<<<<<<< HEAD
import data.UserRepository
import io.ktor.http.*
=======
import com.auth0.jwt.algorithms.Algorithm

import data.repository.ProfileRepository
import data.repository.UserRepository
import data.repository.ConsentimientoRepository 

>>>>>>> 9c708be58c0da2e0fdd1b19afce2e15bc84039ff
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
<<<<<<< HEAD
import routes.auth.authRoutes
=======

import routes.auth.authRoutes
import routes.me.meRoutes
import routes.consent.ConsentRoutes
import com.example.routes.intentosRoutes  // ⬅️ AGREGAR ESTE IMPORT

import security.AuthCtx
import security.AuthCtxKey
>>>>>>> 9c708be58c0da2e0fdd1b19afce2e15bc84039ff



fun Application.configureRouting() {
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
<<<<<<< HEAD
        // Healthcheck simple
        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }

        // Repositorio y rutas públicas de auth
        val userRepo = UserRepository()
        authRoutes(userRepo)  // <- SOLO pasa el repo (coincide con la firma)

        // Ejemplo de rutas protegidas (si tienes el plugin JWT con el name "auth-jwt")
        authenticate("auth-jwt") {
            get("/me") {
                // Aquí podrías leer el principal del JWT y responder datos del usuario
                call.respond(HttpStatusCode.OK, mapOf("status" to "authenticated"))
            }
        }
=======
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
>>>>>>> 9c708be58c0da2e0fdd1b19afce2e15bc84039ff
    }
}