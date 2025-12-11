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
                val q      = call.request.queryParameters["q"]

                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20

                val (items, total) = repo.list(
                    PreguntaRepository.ListParams(
                        activa    = activa,
                        nivel     = nivel,
                        tipoBanco = tipo,
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
        }

        get("/admin/preguntas") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@get call.respond(HttpStatusCode.Forbidden, "Solo admin")

            // Parsear query parameters
            val activa = call.request.queryParameters["activa"]?.toBooleanStrictOrNull()
            val nivel = call.request.queryParameters["nivel"]?.let {
                runCatching { Nivel.valueOf(it) }.getOrNull()
            }
            val tipoBanco = call.request.queryParameters["tipoBanco"]?.let {
                runCatching { TipoBanco.valueOf(it) }.getOrNull()
            }
            val q = call.request.queryParameters["q"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20

            // Validar parámetros
            if (page < 1)
                return@get call.respond(HttpStatusCode.BadRequest, "page debe ser >= 1")
            if (size < 1 || size > 100)
                return@get call.respond(HttpStatusCode.BadRequest, "size debe estar entre 1 y 100")

            val params = PreguntaRepository.ListParams(
                activa = activa,
                nivel = nivel,
                tipoBanco = tipoBanco,
                q = q,
                page = page,
                size = size
            )

            val (items, total) = runCatching { repo.list(params) }
                .getOrElse { ex ->
                    return@get call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error listando preguntas")
                }

            val response = PagedPreguntasRes(
                items = items,
                page = page,
                size = size,
                total = total
            )

            call.respond(HttpStatusCode.OK, response)
        }

        patch("/admin/preguntas/{id}") {
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
    }
}
