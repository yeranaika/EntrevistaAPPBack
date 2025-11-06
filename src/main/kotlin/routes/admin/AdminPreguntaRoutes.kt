package routes.admin

import data.models.CreatePreguntaReq
import data.repository.admin.PreguntaRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.isAdmin

// Esta es la ruta correcta para crear preguntas (NO users)
fun Route.AdminPreguntaCreateRoute(repo: PreguntaRepository) {
    authenticate("auth-jwt") {
        post("/admin/preguntas") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val body = runCatching { call.receive<CreatePreguntaReq>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

            if (body.texto.isBlank())
                return@post call.respond(HttpStatusCode.BadRequest, "texto requerido")

            val created = runCatching { repo.create(body) }
                .getOrElse { ex ->
                    return@post call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error creando pregunta")
                }

            call.respond(HttpStatusCode.Created, created)
        }
    }
}
