package routes.admin

import data.models.cuestionario.AsociarPreguntaRequest
import data.models.cuestionario.CrearPruebaReq
import data.models.cuestionario.PreguntaAsignadaResponse
import data.repository.admin.PruebaRepository
import data.repository.cuestionario.PruebaCuestionarioRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import plugins.DatabaseFactory
import security.isAdmin
import java.util.UUID

@Serializable
data class AsociarPreguntasBatchResponse(
    val message: String,
    val items: List<PreguntaAsignadaResponse>
)

fun Route.AdminPruebaRoutes(repo: PruebaRepository) {
    authenticate("auth-jwt") {
        post("/admin/pruebas") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val body = runCatching { call.receive<CrearPruebaReq>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

            // Validar tipoPrueba (máximo 8 caracteres según la BD)
            if (body.tipoPrueba.length > 8)
                return@post call.respond(HttpStatusCode.BadRequest, "tipoPrueba debe tener máximo 8 caracteres")

            // area y nivel son enums, ya están validados automáticamente por la deserialización
            // metadata es un Map, no necesita validación de longitud aquí

            val created = runCatching { repo.create(body) }
                .getOrElse { ex ->
                    return@post call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error creando prueba")
                }

            call.respond(HttpStatusCode.Created, created)
        }

        get("/admin/pruebas") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@get call.respond(HttpStatusCode.Forbidden, "Solo admin")

            // Parsear query parameters
            val activo = call.request.queryParameters["activo"]?.toBooleanStrictOrNull()
            val nivel = call.request.queryParameters["nivel"]?.let {
                runCatching { data.models.Nivel.valueOf(it) }.getOrNull()
            }
            val area = call.request.queryParameters["area"]?.let {
                runCatching { data.models.cuestionario.AreaPrueba.valueOf(it) }.getOrNull()
            }
            val tipoPrueba = call.request.queryParameters["tipoPrueba"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20

            // Validar parámetros
            if (page < 1)
                return@get call.respond(HttpStatusCode.BadRequest, "page debe ser >= 1")
            if (size < 1 || size > 100)
                return@get call.respond(HttpStatusCode.BadRequest, "size debe estar entre 1 y 100")

            val params = PruebaRepository.ListParams(
                activo = activo,
                nivel = nivel,
                area = area,
                tipoPrueba = tipoPrueba,
                page = page,
                size = size
            )

            val (items, total) = runCatching { repo.list(params) }
                .getOrElse { ex ->
                    return@get call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error listando pruebas")
                }

            val response = data.models.cuestionario.PagedPruebasRes(
                items = items,
                page = page,
                size = size,
                total = total
            )

            call.respond(HttpStatusCode.OK, response)
        }

        get("/admin/pruebas/{id}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@get call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val idParam = call.parameters["id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "id requerido")

            val pruebaId = runCatching { UUID.fromString(idParam) }
                .getOrElse { return@get call.respond(HttpStatusCode.BadRequest, "id inválido") }

            val prueba = runCatching { repo.getPruebaConPreguntas(pruebaId) }
                .getOrElse { ex ->
                    return@get call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error obteniendo prueba")
                }

            if (prueba == null)
                return@get call.respond(HttpStatusCode.NotFound, "Prueba no encontrada")

            call.respond(HttpStatusCode.OK, prueba)
        }

        patch("/admin/pruebas/{id}") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@patch call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val idParam = call.parameters["id"]
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "id requerido")

            val pruebaId = runCatching { UUID.fromString(idParam) }
                .getOrElse { return@patch call.respond(HttpStatusCode.BadRequest, "id inválido") }

            val body = runCatching { call.receive<data.models.cuestionario.ActualizarPruebaReq>() }
                .getOrElse { return@patch call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

            // Validar que al menos un campo esté presente
            if (body.tipoPrueba == null && body.area == null && body.nivel == null &&
                body.metadata == null && body.activo == null)
                return@patch call.respond(HttpStatusCode.BadRequest, "Debe proporcionar al menos un campo a actualizar")

            // Validar tipoPrueba si se proporciona
            if (body.tipoPrueba != null && body.tipoPrueba.length > 8)
                return@patch call.respond(HttpStatusCode.BadRequest, "tipoPrueba debe tener máximo 8 caracteres")

            // Obtener el ID del admin para el histórico
            val adminId = principal.subject

            val updated = runCatching { repo.update(pruebaId, body, adminId) }
                .getOrElse { ex ->
                    return@patch call.respond(HttpStatusCode.InternalServerError, ex.message ?: "Error actualizando prueba")
                }

            if (updated == null)
                return@patch call.respond(HttpStatusCode.NotFound, "Prueba no encontrada")

            call.respond(HttpStatusCode.OK, updated)
        }

        // Nuevo endpoint para asociar múltiples preguntas en batch
        post("/admin/pruebas/{id}/items") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            if (!principal.isAdmin())
                return@post call.respond(HttpStatusCode.Forbidden, "Solo admin")

            val idParam = call.parameters["id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "id requerido")

            val pruebaId = runCatching { UUID.fromString(idParam) }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "id inválido") }

            val items = runCatching { call.receive<List<AsociarPreguntaRequest>>() }
                .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "JSON inválido - se esperaba un array") }

            if (items.isEmpty())
                return@post call.respond(HttpStatusCode.BadRequest, "Debe proporcionar al menos una pregunta")

            // Validar que todos los ordenes sean únicos
            val ordenes = items.map { it.orden }
            if (ordenes.size != ordenes.distinct().size)
                return@post call.respond(HttpStatusCode.BadRequest, "Los órdenes deben ser únicos")

            // Crear repositorio de cuestionario para asociar
            val cuestionarioRepo = PruebaCuestionarioRepository(
                DatabaseFactory.db,
                Json { ignoreUnknownKeys = true }
            )

            val resultados = mutableListOf<PreguntaAsignadaResponse>()

            // Asociar cada pregunta
            for (item in items) {
                val preguntaId = runCatching { UUID.fromString(item.preguntaId) }
                    .getOrElse {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "preguntaId inválido: ${item.preguntaId}")
                        )
                    }

                try {
                    val resultado = cuestionarioRepo.asociarPregunta(pruebaId, preguntaId, item)
                    resultados.add(resultado)
                } catch (e: Exception) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Error al asociar pregunta ${item.preguntaId}: ${e.message}")
                    )
                }
            }

            call.respond(HttpStatusCode.Created, AsociarPreguntasBatchResponse(
                message = "Se asociaron ${resultados.size} preguntas exitosamente",
                items = resultados
            ))
        }
    }
}
