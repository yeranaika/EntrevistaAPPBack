// src/main/kotlin/routes/Routing.kt
package routes

import data.UserRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.auth.authRoutes

fun Application.configureRouting() {
    routing {
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
    }
}
