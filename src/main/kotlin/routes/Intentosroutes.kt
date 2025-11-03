package com.example.routes

import com.example.data.models.*
import com.example.data.repository.IntentoPruebaRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.intentosRoutes() {
    
    authenticate("auth-jwt") {
        
        // ============================================
        // 1) CREAR INTENTO
        // POST /pruebas/{pruebaId}/intentos
        // ============================================
        post("/pruebas/{pruebaId}/intentos") {
            try {
                // Obtener usuario autenticado
                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                // Obtener pruebaId de la URL
                val pruebaId = call.parameters["pruebaId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId requerido"))

                // Validar UUIDs
                val userUUID = try {
                    UUID.fromString(usuarioId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "usuarioId invalido"))
                }

                val pruebaUUID = try {
                    UUID.fromString(pruebaId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "pruebaId invalido"))
                }

                // Crear el intento
                val intento = IntentoPruebaRepository.crearIntento(userUUID, pruebaUUID)

                // Obtener la primera pregunta
                val primeraPregunta = IntentoPruebaRepository.obtenerSiguientePregunta(
                    UUID.fromString(intento.intentoId)
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

        // ============================================
        // 2) OBTENER SIGUIENTE PREGUNTA
        // GET /intentos/{intentoId}/siguiente
        // ============================================
        get("/intentos/{intentoId}/siguiente") {
            try {
                val intentoId = call.parameters["intentoId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoUUID = try {
                    UUID.fromString(intentoId)
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))
                }

                // Verificar que el intento existe y pertenece al usuario
                val intento = IntentoPruebaRepository.obtenerIntento(intentoUUID)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intento no encontrado"))

                // Verificar autorizacion
                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                if (intento.usuarioId != usuarioId) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No autorizado"))
                }

                // Verificar que el intento esta en progreso
                if (intento.estado != EstadoIntento.EN_PROGRESO) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El intento ya fue finalizado")
                    )
                }

                // Obtener siguiente pregunta
                val siguientePregunta = IntentoPruebaRepository.obtenerSiguientePregunta(intentoUUID)

                // Si no hay mas preguntas, finalizar automaticamente
                if (siguientePregunta == null) {
                    val resultado = IntentoPruebaRepository.finalizarIntento(intentoUUID, abandonado = false)
                    return@get call.respond(HttpStatusCode.OK, mapOf(
                        "mensaje" to "Prueba completada",
                        "resultado" to resultado
                    ))
                }

                // Obtener progreso
                val progreso = IntentoPruebaRepository.obtenerProgreso(
                    intentoUUID,
                    totalPreguntas = 10 // TODO: Obtener del objeto prueba
                )

                val response = ObtenerSiguientePreguntaResponse(
                    pregunta = siguientePregunta,
                    progreso = progreso
                )

                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al obtener siguiente pregunta: ${e.message}")
                )
            }
        }

        // ============================================
        // 3) RESPONDER PREGUNTA
        // POST /intentos/{intentoId}/respuestas
        // ============================================
        post("/intentos/{intentoId}/respuestas") {
            try {
                val intentoId = call.parameters["intentoId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoUUID = try {
                    UUID.fromString(intentoId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))
                }

                // Recibir request body
                val request = call.receive<ResponderItemRequest>()

                // Verificar que el intento existe y pertenece al usuario
                val intento = IntentoPruebaRepository.obtenerIntento(intentoUUID)
                    ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intento no encontrado"))

                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                if (intento.usuarioId != usuarioId) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No autorizado"))
                }

                // Verificar que el intento esta en progreso
                if (intento.estado != EstadoIntento.EN_PROGRESO) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "El intento ya fue finalizado")
                    )
                }

                val preguntaUUID = try {
                    UUID.fromString(request.preguntaId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "preguntaId invalido"))
                }

                // Verificar si la respuesta es correcta
                val esCorrecta = IntentoPruebaRepository.verificarRespuesta(
                    preguntaUUID,
                    request.respuestaUsuario
                )

                // Calcular puntaje (ejemplo: 10 puntos por respuesta correcta)
                val puntaje = if (esCorrecta) 10 else 0

                // Calcular orden (numero de respuestas ya guardadas + 1)
                val orden = IntentoPruebaRepository.contarRespuestas(intentoUUID) + 1

                // Guardar respuesta
                val respuesta = IntentoPruebaRepository.guardarRespuesta(
                    intentoId = intentoUUID,
                    preguntaId = preguntaUUID,
                    respuestaUsuario = request.respuestaUsuario,
                    esCorrecta = esCorrecta,
                    puntajeObtenido = puntaje,
                    tiempoRespuestaSegundos = request.tiempoRespuestaSegundos,
                    orden = orden
                )

                // Obtener siguiente pregunta
                val siguientePregunta = IntentoPruebaRepository.obtenerSiguientePregunta(intentoUUID)

                val mensaje = when {
                    esCorrecta -> "Correcto!"
                    else -> "Incorrecto"
                }

                val response = ResponderItemResponse(
                    respuestaId = respuesta.respuestaId,
                    esCorrecta = esCorrecta,
                    puntajeObtenido = puntaje,
                    mensaje = mensaje,
                    siguientePregunta = siguientePregunta
                )

                call.respond(HttpStatusCode.Created, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al guardar respuesta: ${e.message}")
                )
            }
        }

        // ============================================
        // 4) FINALIZAR INTENTO
        // POST /intentos/{intentoId}/finalizar
        // ============================================
        post("/intentos/{intentoId}/finalizar") {
            try {
                val intentoId = call.parameters["intentoId"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoUUID = try {
                    UUID.fromString(intentoId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))
                }

                // Request opcional
                val request = try {
                    call.receive<FinalizarIntentoRequest>()
                } catch (e: Exception) {
                    FinalizarIntentoRequest(abandonado = false)
                }

                // Verificar que el intento existe y pertenece al usuario
                val intento = IntentoPruebaRepository.obtenerIntento(intentoUUID)
                    ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intento no encontrado"))

                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                if (intento.usuarioId != usuarioId) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No autorizado"))
                }

                // Finalizar intento y calcular resultados
                val resultado = IntentoPruebaRepository.finalizarIntento(
                    intentoUUID,
                    abandonado = request.abandonado
                )

                call.respond(HttpStatusCode.OK, resultado)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al finalizar intento: ${e.message}")
                )
            }
        }

        // ============================================
        // 5) OBTENER HISTORIAL DE INTENTOS
        // GET /intentos/mis-intentos
        // ============================================
        get("/intentos/mis-intentos") {
            try {
                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "No autenticado"))

                val userUUID = try {
                    UUID.fromString(usuarioId)
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "usuarioId invalido"))
                }

                val intentos = IntentoPruebaRepository.obtenerIntentosPorUsuario(userUUID)

                // ✅ Convertir de IntentoPrueba a IntentoResponse
                val intentosResponse = intentos.map { intento ->
                    IntentoResponse(
                        intentoId = intento.intentoId,
                        pruebaId = intento.pruebaId,
                        pruebaTitulo = null, // TODO: Obtener del objeto prueba
                        fechaInicio = intento.fechaInicio,
                        fechaFin = intento.fechaFin,
                        puntajeTotal = intento.puntajeTotal,
                        estado = intento.estado,
                        tiempoTotalSegundos = intento.tiempoTotalSegundos
                    )
                }

                val response = MisIntentosResponse(
                    intentos = intentosResponse,
                    total = intentosResponse.size
                )
                
                call.respond(HttpStatusCode.OK, response)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al obtener intentos: ${e.message}")
                )
            }
        }

        // ============================================
        // 6) OBTENER ESTADISTICAS DE UN INTENTO
        // GET /intentos/{intentoId}/estadisticas
        // ============================================
        get("/intentos/{intentoId}/estadisticas") {
            try {
                val intentoId = call.parameters["intentoId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId requerido"))

                val intentoUUID = try {
                    UUID.fromString(intentoId)
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId invalido"))
                }

                // Verificar autorizacion
                val intento = IntentoPruebaRepository.obtenerIntento(intentoUUID)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Intento no encontrado"))

                val principal = call.principal<JWTPrincipal>()
                val usuarioId = principal?.subject
                if (intento.usuarioId != usuarioId) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "No autorizado"))
                }

                // Obtener estadisticas
                val estadisticas = IntentoPruebaRepository.obtenerEstadisticas(intentoUUID)
                    ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Estadisticas no encontradas"))

                call.respond(HttpStatusCode.OK, estadisticas)

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error al obtener estadisticas: ${e.message}")
                )
            }
        }
    }
}