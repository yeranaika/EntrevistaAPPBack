package routes.admin

import data.models.ActualizarPreguntaReq
import data.models.CreatePreguntaReq
import data.models.Nivel
import data.models.PagedPreguntasRes
import data.models.TipoBanco
import data.repository.admin.PreguntaRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.isAdmin
import java.util.UUID

// Ruta admin para preguntas: crea y lista
fun Route.adminPreguntaRoutes(repo: PreguntaRepository) {

    authenticate("auth-jwt") {
        route("/admin/preguntas") {

            // ---------- POST /admin/preguntas (crear) ----------
            post {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isAdmin())
                    return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")

                val body = runCatching { call.receive<CreatePreguntaReq>() }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            "JSON inválido"
                        )
                    }

                if (body.texto.isBlank())
                    return@post call.respond(HttpStatusCode.BadRequest, "texto requerido")
                if (body.sector.isBlank())
                    return@post call.respond(HttpStatusCode.BadRequest, "sector requerido")

                val created = runCatching { repo.create(body) }
                    .getOrElse { ex ->
                        return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            ex.message ?: "Error creando pregunta"
                        )
                    }

                call.respond(HttpStatusCode.Created, created)
            }

            // ---------- GET /admin/preguntas/{id} (obtener una pregunta) ----------
            get("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isAdmin())
                    return@get call.respond(HttpStatusCode.Forbidden, "Solo admin")

                val idParam = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "id requerido")

                val preguntaId = runCatching { UUID.fromString(idParam) }
                    .getOrElse { return@get call.respond(HttpStatusCode.BadRequest, "id inválido") }

                val pregunta = runCatching { repo.getById(preguntaId) }
                    .getOrElse { ex ->
                        return@get call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error obteniendo pregunta")
                    }

                if (pregunta == null)
                    return@get call.respond(HttpStatusCode.NotFound, "Pregunta no encontrada")

                call.respond(HttpStatusCode.OK, pregunta)
            }

            // ---------- GET /admin/preguntas (listar con filtros) ----------
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isAdmin())
                    return@get call.respond(HttpStatusCode.Forbidden, "Solo admin")

                // Helper para enums case-insensitive
                fun <E : Enum<E>> parseEnum(param: String?, values: Array<E>): E? =
                    param?.let { p -> values.firstOrNull { it.name.equals(p, ignoreCase = true) } }

                val activa = call.request.queryParameters["activa"]?.toBooleanStrictOrNull()
                val nivel  = parseEnum(call.request.queryParameters["nivel"], Nivel.values())
                val tipo   = parseEnum(call.request.queryParameters["tipoBanco"], TipoBanco.values())
                val sector = call.request.queryParameters["sector"]
                val q      = call.request.queryParameters["q"]

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20

                val (items, total) = repo.list(
                    PreguntaRepository.ListParams(
                        activa    = activa,
                        nivel     = nivel,
                        tipoBanco = tipo,
                        sector    = sector,
                        q         = q,
                        page      = page,
                        size      = size
                    )
                )

                val res = PagedPreguntasRes(
                    items = items,
                    page  = page,
                    size  = size,
                    total = total
                )

                call.respond(res)
            }

            // ---------- PATCH /admin/preguntas/{id} (actualizar) ----------
            patch("/{id}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@patch call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val idParam = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "id requerido")

            val preguntaId = runCatching { UUID.fromString(idParam) }
                .getOrElse { return@patch call.respond(HttpStatusCode.BadRequest, "id inválido") }

            val body = runCatching { call.receive<ActualizarPreguntaReq>() }
                .getOrElse { return@patch call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

            // Validar que al menos un campo esté presente
            if (body.texto == null && body.sector == null && body.activa == null && body.pistas == null && body.historica == null)
                return@patch call.respond(HttpStatusCode.BadRequest, "Debe proporcionar al menos un campo a actualizar")

            // Validar texto si se proporciona
            if (body.texto != null && body.texto.isBlank())
                return@patch call.respond(HttpStatusCode.BadRequest, "texto no puede estar vacío")

            // Obtener el ID del admin para el histórico
            val adminId = principal.subject

            val updated = runCatching { repo.update(preguntaId, body, adminId) }
                .getOrElse { ex ->
                    return@patch call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error actualizando pregunta")
                }

            if (updated == null)
                return@patch call.respond(HttpStatusCode.NotFound, "Pregunta no encontrada")

            call.respond(HttpStatusCode.OK, updated)
            }

            // ---------- DELETE /admin/preguntas/{id} (eliminar) ----------
            delete("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                if (!principal.isAdmin())
                    return@delete call.respond(HttpStatusCode.Forbidden, "Solo admin")

                val idParam = call.parameters["id"]
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "id requerido")

                val preguntaId = runCatching { UUID.fromString(idParam) }
                    .getOrElse { return@delete call.respond(HttpStatusCode.BadRequest, "id inválido") }

                val deleted = runCatching { repo.delete(preguntaId) }
                    .getOrElse { ex ->
                        return@delete call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error eliminando pregunta")
                    }

                if (!deleted)
                    return@delete call.respond(HttpStatusCode.NotFound, "Pregunta no encontrada")

                call.respond(HttpStatusCode.OK, mapOf("message" to "Pregunta eliminada exitosamente"))
            }
        }
    }
}
