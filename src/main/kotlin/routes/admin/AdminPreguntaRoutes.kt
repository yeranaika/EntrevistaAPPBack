package routes.admin

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
    }
}
