package routes.cuestionario.prueba

import com.example.data.models.CrearIntentoResponse
import com.example.data.models.EstadoIntento
import com.example.data.models.FinalizarIntentoRequest
import com.example.data.models.IntentoPrueba
import com.example.data.models.ResponderItemRequest
import com.example.data.models.ResponderItemResponse
import com.example.data.repository.IntentoPruebaRepository
import data.models.cuestionario.AsociarPreguntaRequest
import data.repository.cuestionario.PruebaCuestionarioRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import plugins.DatabaseFactory
import security.isAdmin
import java.util.UUID

private val defaultJsonForPruebaRoutes by lazy { Json { ignoreUnknownKeys = true } }

fun Route.pruebaRoutes(
    repo: PruebaCuestionarioRepository = PruebaCuestionarioRepository(
        DatabaseFactory.db,
        defaultJsonForPruebaRoutes
    )
) {
    authenticate("auth-jwt") {
        route("/cuestionario") {
            post("/pruebas/{pruebaId}/preguntas") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                if (!principal.isAdmin()) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Solo administradores"))
                }

                val pruebaIdParam = call.parameters["pruebaId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId requerido"))

                val pruebaId = pruebaIdParam.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId invalido"))

                val payload = runCatching { call.receive<AsociarPreguntaRequest>() }
                    .getOrElse {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "JSON invalido"))
                    }

                val preguntaId = payload.preguntaId.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "preguntaId invalido"))

                try {
                    val result = repo.asociarPregunta(pruebaId, preguntaId, payload)
                    call.respond(HttpStatusCode.Created, result)
                } catch (iae: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (iae.message ?: "Datos invalidos")))
                } catch (ise: IllegalStateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to (ise.message ?: "Orden duplicado")))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al asociar pregunta: ${e.message}")
                    )
                }
            }

            post("/pruebas/{pruebaId}/intentos") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val usuarioId = principal.subject?.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Token invalido"))

                val pruebaIdParam = call.parameters["pruebaId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId requerido"))

                val pruebaId = pruebaIdParam.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId invalido"))

                try {
                    val totalPreguntas = repo.totalPreguntas(pruebaId)
                    if (totalPreguntas == 0) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "La prueba no tiene preguntas asignadas")
                        )
                    }

                    val intento = IntentoPruebaRepository.crearIntento(usuarioId, pruebaId)
                    val primeraPregunta = repo.obtenerPrimeraPregunta(pruebaId)
                        ?: return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "No se pudo recuperar la primera pregunta")
                        )

                    val response = CrearIntentoResponse(
                        intentoId = intento.intentoId,
                        pruebaId = intento.pruebaId,
                        fechaInicio = intento.fechaInicio,
                        estado = intento.estado,
                        primeraPregunta = primeraPregunta
                    )

                    call.respond(HttpStatusCode.Created, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al crear intento: ${e.message}")
                    )
                }
            }
        }

        route("/cuestionario/intentos") {
            get("/{intentoId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val intentoIdParam = call.parameters["intentoId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoId = intentoIdParam.toUUIDOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))

                try {
                    val intento = IntentoPruebaRepository.obtenerIntento(intentoId)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Intento no encontrado")
                        )

                    if (!perteneceAlUsuario(principal, intento)) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "No autorizado para ver este intento")
                        )
                    }

                    val pruebaId = intento.pruebaId.toUUIDOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "pruebaId invalido")
                        )

                    val totalPreguntas = repo.totalPreguntas(pruebaId)
                    val progreso = IntentoPruebaRepository.obtenerProgreso(intentoId, totalPreguntas)

                    val response = mapOf(
                        "intento" to intento,
                        "progreso" to progreso
                    )

                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al obtener intento: ${e.message}")
                    )
                }
            }

            get("/{intentoId}/siguiente") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val intentoIdParam = call.parameters["intentoId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoId = intentoIdParam.toUUIDOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))

                try {
                    val intento = IntentoPruebaRepository.obtenerIntento(intentoId)
                        ?: return@get call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Intento no encontrado")
                        )

                    if (!perteneceAlUsuario(principal, intento)) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "No autorizado para este intento")
                        )
                    }

                    if (intento.estado != EstadoIntento.EN_PROGRESO) {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "El intento ya no está en progreso")
                        )
                    }

                    val pruebaId = intento.pruebaId.toUUIDOrNull()
                        ?: return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "pruebaId invalido")
                        )

                    // Contar cuántas respuestas hay registradas
                    val totalRespuestas = IntentoPruebaRepository.contarRespuestas(intentoId)

                    // Obtener la siguiente pregunta según el orden
                    val siguientePregunta = if (totalRespuestas == 0) {
                        // Si no hay respuestas, obtener la primera pregunta
                        repo.obtenerPrimeraPregunta(pruebaId)
                    } else {
                        // Obtener la siguiente pregunta después del último orden respondido
                        repo.obtenerSiguientePregunta(pruebaId, totalRespuestas)
                    }

                    if (siguientePregunta == null) {
                        // No hay más preguntas, indicar finalización pendiente
                        return@get call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "finalizacionPendiente" to true,
                                "mensaje" to "No hay más preguntas. Finaliza el intento para ver tus resultados.",
                                "totalRespondidas" to totalRespuestas
                            )
                        )
                    }

                    // Devolver la siguiente pregunta
                    call.respond(HttpStatusCode.OK, siguientePregunta)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al obtener siguiente pregunta: ${e.message}")
                    )
                }
            }

            post("/{intentoId}/respuestas") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val intentoIdParam = call.parameters["intentoId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoId = intentoIdParam.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))

                val payload = runCatching { call.receive<ResponderItemRequest>() }
                    .getOrElse {
                        return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "JSON invalido"))
                    }

                val pruebaPreguntaId = payload.pruebaPreguntaId.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaPreguntaId invalido"))

                if (payload.respuestaUsuario.isBlank()) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "La respuesta no puede estar vacia"))
                }

                try {
                    val intento = IntentoPruebaRepository.obtenerIntento(intentoId)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Intento no encontrado")
                        )

                    if (!perteneceAlUsuario(principal, intento)) {
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "No autorizado para este intento")
                        )
                    }

                    if (intento.estado != EstadoIntento.EN_PROGRESO) {
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "El intento ya no acepta respuestas")
                        )
                    }

                    // Verificar que no se haya respondido ya esta pregunta
                    if (repo.respuestaYaRegistrada(intentoId, pruebaPreguntaId)) {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "La pregunta ya fue respondida")
                        )
                    }

                    val pruebaId = intento.pruebaId.toUUIDOrNull()
                        ?: return@post call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "pruebaId invalido en el intento")
                        )

                    // Buscar la pregunta en prueba_pregunta para obtener clave_correcta
                    val asignacionRow = repo.obtenerAsignacionPorId(pruebaPreguntaId)
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "La pregunta no pertenece a esta prueba")
                        )

                    // Evaluación automática si tiene clave_correcta
                    val esCorrecta = asignacionRow.claveCorrecta
                        ?.let { clave -> clave.equals(payload.respuestaUsuario.trim(), ignoreCase = true) }

                    // TODO: Generar feedback con IA (InspecL) si es necesario
                    val feedbackInspecl: String? = null

                    // Guardar respuesta con evaluación
                    val respuesta = IntentoPruebaRepository.guardarRespuesta(
                        intentoId = intentoId,
                        pruebaPreguntaId = pruebaPreguntaId,
                        respuestaUsuario = payload.respuestaUsuario,
                        esCorrecta = esCorrecta,
                        feedbackInspecl = feedbackInspecl
                    )

                    // Actualizar puntaje en tiempo real
                    val puntajeActual = IntentoPruebaRepository.actualizarPuntaje(intentoId)

                    val siguientePregunta = repo.obtenerSiguientePregunta(pruebaId, asignacionRow.orden)
                    val mensaje = when (esCorrecta) {
                        true -> "Respuesta correcta"
                        false -> "Respuesta incorrecta"
                        null -> "Respuesta registrada"
                    }

                    val response = ResponderItemResponse(
                        respuestaId = respuesta.respuestaId,
                        esCorrecta = esCorrecta,
                        feedbackInspecl = feedbackInspecl,
                        mensaje = mensaje,
                        siguientePregunta = siguientePregunta
                    )

                    call.respond(HttpStatusCode.Created, response)
                } catch (iae: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (iae.message ?: "Datos invalidos")))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al guardar respuesta: ${e.message}")
                    )
                }
            }

            post("/{intentoId}/finalizar") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val intentoIdParam = call.parameters["intentoId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoId = intentoIdParam.toUUIDOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))

                val payload = runCatching { call.receive<FinalizarIntentoRequest>() }
                    .getOrElse { FinalizarIntentoRequest() }

                try {
                    val intento = IntentoPruebaRepository.obtenerIntento(intentoId)
                        ?: return@post call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "Intento no encontrado")
                        )

                    if (!perteneceAlUsuario(principal, intento)) {
                        return@post call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "No autorizado para este intento")
                        )
                    }

                    val resultado = IntentoPruebaRepository.finalizarIntento(
                        intentoId = intentoId,
                        abandonado = payload.abandonado
                    )

                    call.respond(HttpStatusCode.OK, resultado)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Error al finalizar intento: ${e.message}")
                    )
                }
            }
        }
    }
}

private fun String.toUUIDOrNull(): UUID? =
    runCatching { UUID.fromString(this) }.getOrNull()

private fun perteneceAlUsuario(principal: JWTPrincipal, intento: IntentoPrueba): Boolean =
    principal.subject?.let { it == intento.usuarioId } == true
