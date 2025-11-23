package routes.admin

import data.models.CreatePreguntaNivelacionReq
import data.repository.nivelacion.PreguntaNivelacionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * Rutas de administración para preguntas de nivelación
 * Solo admins pueden crear/editar/eliminar preguntas
 */
fun Route.adminPreguntaNivelacionRoutes(
    preguntaRepo: PreguntaNivelacionRepository
) {
    authenticate("auth-jwt") {
        route("/admin/preguntas-nivelacion") {

            // POST /admin/preguntas-nivelacion - Crear una nueva pregunta
            post {
                val request = call.receive<CreatePreguntaNivelacionReq>()

                // Validaciones básicas
                if (request.enunciado.isBlank()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El enunciado es requerido")
                    )
                }

                if (request.opciones.size < 2) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Debe haber al menos 2 opciones")
                    )
                }

                if (request.respuestaCorrecta < 0 || request.respuestaCorrecta >= request.opciones.size) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Índice de respuesta correcta inválido")
                    )
                }

                if (request.dificultad !in 1..3) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "La dificultad debe estar entre 1 y 3")
                    )
                }

                // Crear pregunta
                val preguntaId = preguntaRepo.createSimple(
                    habilidad = request.habilidad,
                    dificultad = request.dificultad,
                    enunciado = request.enunciado,
                    opciones = request.opciones,
                    respuestaCorrecta = request.respuestaCorrecta,
                    explicacion = request.explicacion,
                    activa = request.activa
                )

                call.respond(
                    HttpStatusCode.Created,
                    mapOf(
                        "id" to preguntaId.toString(),
                        "message" to "Pregunta creada exitosamente"
                    )
                )
            }

            // GET /admin/preguntas-nivelacion?habilidad=logica - Listar preguntas
            get {
                val habilidad = call.request.queryParameters["habilidad"]
                val incluirInactivas = call.request.queryParameters["incluirInactivas"]?.toBoolean() ?: false

                val preguntas = if (habilidad != null) {
                    preguntaRepo.findByHabilidad(habilidad, activasOnly = !incluirInactivas)
                } else {
                    // Si no se especifica habilidad, devolver error o todas (por ahora error)
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Debe especificar una habilidad")
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    preguntas.map { pregunta ->
                        mapOf(
                            "id" to pregunta.id.toString(),
                            "habilidad" to pregunta.habilidad,
                            "dificultad" to pregunta.dificultad,
                            "enunciado" to pregunta.enunciado,
                            "opciones" to pregunta.opciones,
                            "respuestaCorrecta" to pregunta.respuestaCorrecta,
                            "explicacion" to pregunta.explicacion,
                            "activa" to pregunta.activa,
                            "fechaCreacion" to pregunta.fechaCreacion.toString()
                        )
                    }
                )
            }

            // GET /admin/preguntas-nivelacion/{id} - Ver pregunta específica
            get("/{id}") {
                val preguntaId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "ID inválido")
                    )

                val pregunta = preguntaRepo.findDetalleById(preguntaId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Pregunta no encontrada")
                    )

                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "id" to pregunta.id.toString(),
                        "habilidad" to pregunta.habilidad,
                        "dificultad" to pregunta.dificultad,
                        "enunciado" to pregunta.enunciado,
                        "opciones" to pregunta.opciones,
                        "respuestaCorrecta" to pregunta.respuestaCorrecta,
                        "explicacion" to pregunta.explicacion,
                        "activa" to pregunta.activa,
                        "fechaCreacion" to pregunta.fechaCreacion.toString()
                    )
                )
            }

            // PUT /admin/preguntas-nivelacion/{id} - Actualizar pregunta
            put("/{id}") {
                val preguntaId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@put call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "ID inválido")
                    )

                val request = call.receive<CreatePreguntaNivelacionReq>()

                val updated = preguntaRepo.update(
                    id = preguntaId,
                    enunciado = request.enunciado,
                    opciones = request.opciones,
                    respuestaCorrecta = request.respuestaCorrecta,
                    explicacion = request.explicacion,
                    dificultad = request.dificultad,
                    activa = request.activa
                )

                if (updated == 0) {
                    return@put call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Pregunta no encontrada")
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Pregunta actualizada exitosamente")
                )
            }

            // DELETE /admin/preguntas-nivelacion/{id} - Desactivar pregunta
            delete("/{id}") {
                val preguntaId = call.parameters["id"]?.let { UUID.fromString(it) }
                    ?: return@delete call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "ID inválido")
                    )

                val deleted = preguntaRepo.deactivate(preguntaId)

                if (deleted == 0) {
                    return@delete call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Pregunta no encontrada")
                    )
                }

                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Pregunta desactivada exitosamente")
                )
            }
        }
    }
}
