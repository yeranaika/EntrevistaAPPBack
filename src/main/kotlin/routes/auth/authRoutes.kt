package routes.auth

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.http.*

//dataformat
import routes.auth.RegisterRequest

import data.UserRepository
import java.util.UUID

fun Route.authRoutes(userRepo: UserRepository) {
    route("/auth") {
        post("/register") {
            // Ktor deserializa a partir de ContentNegotiation { json() }
            val req = try {
                call.receive<RegisterRequest>()
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_json"))
                return@post
            }

            // Ejemplo de validación mínima
            if (req.email.isBlank() || req.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "missing_fields"))
                return@post
            }

            // Lógica con tu repositorio (mapear a tu tabla/entidad)
            val exists = userRepo.existsByEmail(req.email)
            if (exists) {
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "email_taken"))
                return@post
            }

            // Aquí deberías hashear la contraseña y guardar (ejemplo)
            val userId = UUID.randomUUID()
            // userRepo.create(userId, req.email, hashPassword(req.password), req.name)

            call.respond(HttpStatusCode.Created, mapOf("id" to userId.toString()))
        }
    }
}
