package routes.historial

import data.repository.nivelacion.TestNivelacionRepository
import data.repository.sesiones.SesionEntrevistaRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.UUID

// Helper para extraer userId del JWT
private fun JWTPrincipal.userIdFromJwt(): UUID {
    val sub = this.payload.getClaim("sub").asString()
    return UUID.fromString(sub)
}

@Serializable
data class HistorialItemDto(
    val id: String,
    val tipo: String, // "TEST" o "ENTREVISTA"
    val titulo: String, // "Test de React" o "Entrevista Java"
    val fecha: String,
    val puntaje: Int?, // 0-100 o null
    val nivel: String?, // "Junior", "Mid", "Senior" o null
    val estado: String // "Completado", "Pendiente"
)

@Serializable
data class PaginationMetaDto(
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int
)

@Serializable
data class HistorialResponseDto(
    val data: List<HistorialItemDto>,
    val meta: PaginationMetaDto
)

@Serializable
data class DetalleHistorialResponse(
    val tipo: String, // "TEST" o "ENTREVISTA"
    val test: TestNivelacionDto? = null,
    val entrevista: SesionEntrevistaDto? = null
)

@Serializable
data class TestNivelacionDto(
    val id: String,
    val habilidad: String,
    val puntaje: Int,
    val totalPreguntas: Int,
    val preguntasCorrectas: Int,
    val nivelSugerido: Int,
    val feedback: String?,
    val fechaCompletado: String
)

@Serializable
data class SesionEntrevistaDto(
    val sesionId: String,
    val modo: String,
    val nivel: String,
    val fechaInicio: String,
    val fechaFin: String?,
    val puntajeGeneral: Int?
)

fun Route.historialRoutes(
    sesionRepo: SesionEntrevistaRepository,
    testRepo: TestNivelacionRepository
) {
    route("/historial") {
        authenticate("auth-jwt") {
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdFromJwt()

                val tipoFilter = call.request.queryParameters["tipo"] // TEST, ENTREVISTA, null=ALL
                // Pagination params could be added here

                val historial = mutableListOf<HistorialItemDto>()

                // 1. Fetch Tests
                if (tipoFilter == null || tipoFilter == "TEST") {
                    val tests = testRepo.findByUsuario(userId)
                    historial.addAll(tests.map { t ->
                        HistorialItemDto(
                            id = t.id.toString(),
                            tipo = "TEST",
                            titulo = "Test: ${t.area?.removePrefix("JOB:") ?: "General"}",
                            fecha = t.fechaCompletado ?: "",
                            puntaje = t.puntaje,
                            nivel = when (t.nivel?.lowercase()) {
                                "jr" -> "Junior"
                                "mid" -> "Semi-Senior"
                                "sr" -> "Senior"
                                else -> "N/A"
                            },
                            estado = "Completado"
                        )
                    })
                }

                // 2. Fetch Interviews (Sesiones)
                if (tipoFilter == null || tipoFilter == "ENTREVISTA") {
                    val sesiones = sesionRepo.findByUsuarioId(userId, limit = 50) // TODO: Pagination
                    historial.addAll(sesiones.map { s ->
                        HistorialItemDto(
                            id = s.sesionId.toString(),
                            tipo = "ENTREVISTA",
                            titulo = "Entrevista ${s.modo} - ${s.nivel}",
                            fecha = s.fechaInicio.format(DateTimeFormatter.ISO_DATE_TIME),
                            puntaje = s.puntajeGeneral?.toInt(),
                            nivel = s.nivel,
                            estado = if (s.fechaFin != null) "Completado" else "En Progreso"
                        )
                    })
                }

                // 3. Sort by date descending
                historial.sortByDescending { it.fecha }

                // 4. Apply Pagination
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                
                val fromIndex = (page - 1) * limit
                val toIndex = fromIndex + limit

                val paginatedResult = if (fromIndex < historial.size) {
                    historial.subList(fromIndex, minOf(toIndex, historial.size))
                } else {
                    emptyList()
                }

                call.respond(
                    HistorialResponseDto(
                        data = paginatedResult,
                        meta = PaginationMetaDto(
                            total = historial.size,
                            page = page,
                            limit = limit,
                            totalPages = (historial.size + limit - 1) / limit
                        )
                    )
                )
            }

            get("/{id}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val idParam = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Falta ID")

                val id = try {
                    UUID.fromString(idParam)
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.BadRequest, "ID inválido")
                }

                // Try finding in Tests
                val testRow = testRepo.findById(id)
                if (testRow != null) {
                    return@get call.respond(
                        DetalleHistorialResponse(
                            tipo = "TEST",
                            test = TestNivelacionDto(
                                id = testRow.id.toString(),
                                habilidad = testRow.area ?: "",
                                puntaje = testRow.puntaje,
                                totalPreguntas = testRow.totalPreguntas,
                                preguntasCorrectas = testRow.preguntasCorrectas,
                                nivelSugerido = when (testRow.nivel?.lowercase()) {
                                    "jr" -> 1
                                    "mid" -> 2
                                    "sr" -> 3
                                    else -> 1
                                },
                                feedback = testRow.feedback,
                                fechaCompletado = testRow.fechaCompletado ?: ""
                            )
                        )
                    )
                }

                // Try finding in Sessions
                val sesionRow = sesionRepo.findById(id)
                if (sesionRow != null) {
                    return@get call.respond(
                        DetalleHistorialResponse(
                            tipo = "ENTREVISTA",
                            entrevista = SesionEntrevistaDto(
                                sesionId = sesionRow.sesionId.toString(),
                                modo = sesionRow.modo,
                                nivel = sesionRow.nivel,
                                fechaInicio = sesionRow.fechaInicio.format(DateTimeFormatter.ISO_DATE_TIME),
                                fechaFin = sesionRow.fechaFin?.format(DateTimeFormatter.ISO_DATE_TIME),
                                puntajeGeneral = sesionRow.puntajeGeneral?.toInt()
                            )
                        )
                    )
                }

                call.respond(HttpStatusCode.NotFound, "No se encontró el historial con ese ID")
            }
        }
    }
}
