package routes.sesiones

import data.repository.sesiones.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import services.FeedbackService
import services.MockFeedbackService
import java.util.UUID

/**
 * DTO genérico para mensajes de error.
 */
@Serializable
data class ErrorRes(val error: String)

/**
 * Helper para extraer el userId del JWT.
 */
private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

/**
 * Configuración de rutas para sesiones de entrevista tipo chat.
 */
fun Route.sesionesRoutes(
    sesionRepo: SesionEntrevistaRepository = SesionEntrevistaRepository(),
    sesionPreguntaRepo: SesionPreguntaRepository = SesionPreguntaRepository(),
    respuestaRepo: RespuestaRepository = RespuestaRepository(),
    retroRepo: RetroalimentacionRepository = RetroalimentacionRepository(),
    feedbackService: FeedbackService = MockFeedbackService()
) {
    authenticate("auth-jwt") {
        route("/sesiones") {

            /**
             * POST /sesiones - Crear nueva sesión de entrevista
             *
             * Body: CrearSesionReq { modo: "tec|soft|mix", nivel: "jr|mid|sr" }
             * Response: SesionCreadaRes
             */
            post {
                val usuarioId = call.userIdFromJwt()
                val req = call.receive<CrearSesionReq>()

                call.application.environment.log.info("🆕 [CREAR_SESION] Usuario $usuarioId creando sesión: modo=${req.modo}, nivel=${req.nivel}")

                // Validar modo
                if (req.modo !in listOf("tec", "soft", "mix")) {
                    call.application.environment.log.warn("⚠️ [CREAR_SESION] Modo inválido: ${req.modo}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("Modo inválido. Debe ser: tec, soft, mix")
                    )
                }

                // Validar nivel
                if (req.nivel !in listOf("jr", "mid", "sr")) {
                    call.application.environment.log.warn("⚠️ [CREAR_SESION] Nivel inválido: ${req.nivel}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("Nivel inválido. Debe ser: jr, mid, sr")
                    )
                }

                // Crear sesión
                val sesion = sesionRepo.create(
                    usuarioId = usuarioId,
                    modo = req.modo,
                    nivel = req.nivel,
                    esPremium = false  // MVP: todas las sesiones son free
                )

                call.application.environment.log.info("✅ [CREAR_SESION] Sesión creada exitosamente: ${sesion.sesionId}")

                call.respond(
                    HttpStatusCode.Created,
                    SesionCreadaRes(
                        sessionId = sesion.sesionId.toString(),
                        modo = sesion.modo,
                        nivel = sesion.nivel,
                        fechaInicio = sesion.fechaInicio.toString()
                    )
                )
            }

            /**
             * POST /sesiones/{sessionId}/preguntas - Obtener siguiente pregunta
             *
             * Response: PreguntaRes
             */
            post("/{sessionId}/preguntas") {
                val usuarioId = call.userIdFromJwt()
                val sessionIdStr = call.parameters["sessionId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId requerido")
                    )

                val sessionId = try {
                    UUID.fromString(sessionIdStr)
                } catch (e: IllegalArgumentException) {
                    call.application.environment.log.error("❌ [PREGUNTAS] sessionId inválido: $sessionIdStr")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId inválido")
                    )
                }

                call.application.environment.log.info("📝 [PREGUNTAS] Usuario $usuarioId solicitando pregunta para sesión $sessionId")

                // Verificar que la sesión existe y pertenece al usuario
                val sesion = sesionRepo.findById(sessionId)
                if (sesion == null) {
                    call.application.environment.log.error("❌ [PREGUNTAS] Sesión $sessionId NO EXISTE en BD")
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("Sesión no encontrada")
                    )
                }

                call.application.environment.log.info("✅ [PREGUNTAS] Sesión encontrada. Usuario dueño: ${sesion.usuarioId}")

                if (sesion.usuarioId != usuarioId) {
                    call.application.environment.log.error("🚫 [PREGUNTAS] Usuario $usuarioId NO es dueño de sesión $sessionId")
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorRes("No tienes permiso para acceder a esta sesión")
                    )
                }

                // Verificar que la sesión no esté finalizada
                if (sesion.fechaFin != null) {
                    call.application.environment.log.warn("⚠️ [PREGUNTAS] Sesión $sessionId ya finalizada: ${sesion.fechaFin}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("La sesión ya ha finalizado")
                    )
                }

                // Obtener preguntas ya usadas
                val preguntasUsadas = sesionPreguntaRepo.getPreguntasUsadas(sessionId)
                call.application.environment.log.info("📊 [PREGUNTAS] Preguntas ya usadas: ${preguntasUsadas.size} (${preguntasUsadas.take(3)}...)")

                // Seleccionar siguiente pregunta
                call.application.environment.log.info("🔎 [PREGUNTAS] Buscando siguiente pregunta: modo=${sesion.modo}, nivel=${sesion.nivel}")
                val pregunta = sesionPreguntaRepo.getNextPregunta(
                    sessionId = sessionId,
                    modo = sesion.modo,
                    nivel = sesion.nivel,
                    preguntasUsadas = preguntasUsadas
                )

                if (pregunta == null) {
                    call.application.environment.log.warn("⚠️ [PREGUNTAS] No hay más preguntas disponibles para modo=${sesion.modo}, nivel=${sesion.nivel}")
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("No hay más preguntas disponibles. Finaliza la sesión.")
                    )
                }

                call.application.environment.log.info("✅ [PREGUNTAS] Pregunta seleccionada: ${pregunta.id}")

                // Crear registro session_pregunta
                val orden = preguntasUsadas.size + 1
                val sessionPregunta = sesionPreguntaRepo.create(
                    sessionId = sessionId,
                    preguntaId = pregunta.id,
                    orden = orden
                )

                call.application.environment.log.info("✅ [PREGUNTAS] session_pregunta creada: ${sessionPregunta.sesionPreguntaId}, orden=$orden")

                // Parsear pistas JSON si existen
                val pistasJson = pregunta.pistas?.let {
                    try {
                        kotlinx.serialization.json.Json.parseToJsonElement(it) as? JsonObject
                    } catch (e: Exception) {
                        null
                    }
                }

                call.respond(
                    PreguntaRes(
                        sessionPreguntaId = sessionPregunta.sesionPreguntaId.toString(),
                        preguntaId = pregunta.id.toString(),
                        texto = pregunta.texto,
                        pistas = pistasJson,
                        orden = orden
                    )
                )
            }

            /**
             * POST /sesiones/{sessionId}/responder - Responder una pregunta
             *
             * Body: ResponderReq { sessionPreguntaId, texto }
             * Response: FeedbackRes
             */
            post("/{sessionId}/responder") {
                val usuarioId = call.userIdFromJwt()
                val sessionIdStr = call.parameters["sessionId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId requerido")
                    )

                val sessionId = try {
                    UUID.fromString(sessionIdStr)
                } catch (e: IllegalArgumentException) {
                    call.application.environment.log.error("sessionId inválido recibido: $sessionIdStr")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId inválido")
                    )
                }

                // LOG 1: Verificar qué usuario está llamando
                call.application.environment.log.info("🔍 [RESPONDER] Usuario $usuarioId intentando responder en sesión $sessionId")

                val req = call.receive<ResponderReq>()
                call.application.environment.log.info("📝 [RESPONDER] sessionPreguntaId recibido: ${req.sessionPreguntaId}, texto length: ${req.texto.length}")

                // Validar que el texto de la respuesta no esté vacío
                if (req.texto.trim().isEmpty()) {
                    call.application.environment.log.warn("⚠️ [RESPONDER] Texto de respuesta vacío")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("El texto de la respuesta no puede estar vacío")
                    )
                }

                // Verificar sesión
                call.application.environment.log.info("🔎 [RESPONDER] Buscando sesión en BD: $sessionId")
                val sesion = sesionRepo.findById(sessionId)

                // LOG 2: ¿Se encontró la sesión?
                if (sesion == null) {
                    call.application.environment.log.error("❌ [RESPONDER] Sesión $sessionId NO EXISTE en BD")
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("Sesión no encontrada")
                    )
                }

                // LOG 3: ¿Pertenece al usuario?
                call.application.environment.log.info("✅ [RESPONDER] Sesión encontrada. Usuario dueño: ${sesion.usuarioId}, Usuario actual: $usuarioId")

                if (sesion.usuarioId != usuarioId) {
                    call.application.environment.log.error("🚫 [RESPONDER] Usuario $usuarioId NO es dueño de sesión $sessionId (dueño: ${sesion.usuarioId})")
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorRes("No tienes permiso para acceder a esta sesión")
                    )
                }

                call.application.environment.log.info("✅ [RESPONDER] Validación de sesión exitosa")

                // Validar session_pregunta
                val sessionPreguntaId = try {
                    UUID.fromString(req.sessionPreguntaId)
                } catch (e: IllegalArgumentException) {
                    call.application.environment.log.error("❌ [RESPONDER] sessionPreguntaId inválido: ${req.sessionPreguntaId}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionPreguntaId inválido")
                    )
                }

                call.application.environment.log.info("🔎 [RESPONDER] Buscando session_pregunta: $sessionPreguntaId")
                val sessionPregunta = sesionPreguntaRepo.findById(sessionPreguntaId)

                if (sessionPregunta == null) {
                    call.application.environment.log.error("❌ [RESPONDER] session_pregunta $sessionPreguntaId NO EXISTE en BD")
                    return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("Pregunta de sesión no encontrada")
                    )
                }

                call.application.environment.log.info("✅ [RESPONDER] session_pregunta encontrada. sesionId: ${sessionPregunta.sesionId}, preguntaId: ${sessionPregunta.preguntaId}")

                // Verificar que la session_pregunta pertenece a esta sesión
                if (sessionPregunta.sesionId != sessionId) {
                    call.application.environment.log.error("❌ [RESPONDER] session_pregunta $sessionPreguntaId pertenece a sesión ${sessionPregunta.sesionId}, no a $sessionId")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("La pregunta no pertenece a esta sesión")
                    )
                }

                // Verificar que no se haya respondido ya
                call.application.environment.log.info("🔎 [RESPONDER] Verificando si ya existe respuesta para session_pregunta $sessionPreguntaId")
                val respuestaExistente = respuestaRepo.findBySesionPreguntaId(sessionPreguntaId)
                if (respuestaExistente != null) {
                    call.application.environment.log.warn("⚠️ [RESPONDER] Pregunta ya respondida. respuestaId: ${respuestaExistente.respuestaId}")
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("Esta pregunta ya ha sido respondida")
                    )
                }

                call.application.environment.log.info("✅ [RESPONDER] Todas las validaciones pasadas. Creando respuesta...")

                // Crear respuesta
                val respuesta = respuestaRepo.create(
                    sessionPreguntaId = sessionPreguntaId,
                    usuarioId = usuarioId,
                    texto = req.texto.trim()
                )

                // Obtener el texto de la pregunta para generar feedback
                val preguntaId = sessionPregunta.preguntaId
                    ?: return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorRes("No se pudo obtener la pregunta asociada")
                    )

                // Buscar la pregunta original (necesitamos el texto)
                val pregunta = sesionPreguntaRepo.getNextPregunta(
                    sessionId = sessionId,
                    modo = sesion.modo,
                    nivel = sesion.nivel,
                    preguntasUsadas = listOf(preguntaId)
                )

                // En caso de que no encontremos la pregunta, usar un texto genérico
                val preguntaTexto = pregunta?.texto ?: "Pregunta de entrevista"

                // Generar feedback usando el servicio (mock)
                val feedback = feedbackService.generarFeedback(
                    preguntaTexto = preguntaTexto,
                    respuestaTexto = req.texto.trim(),
                    nivel = sesion.nivel
                )

                // Guardar retroalimentación
                retroRepo.create(
                    respuestaId = respuesta.respuestaId,
                    nivelFeedback = feedback.nivelFeedback,
                    enunciado = feedback.enunciado,
                    aciertos = feedback.aciertos,
                    faltantes = feedback.faltantes
                )

                // Retornar feedback
                call.respond(
                    FeedbackRes(
                        nivelFeedback = feedback.nivelFeedback,
                        enunciado = feedback.enunciado,
                        aciertos = feedback.aciertos,
                        faltantes = feedback.faltantes
                    )
                )
            }

            /**
             * POST /sesiones/{sessionId}/finalizar - Finalizar sesión
             *
             * Response: ResumenFinalRes
             */
            post("/{sessionId}/finalizar") {
                val usuarioId = call.userIdFromJwt()
                val sessionIdStr = call.parameters["sessionId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId requerido")
                    )

                val sessionId = try {
                    UUID.fromString(sessionIdStr)
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("sessionId inválido")
                    )
                }

                // Verificar sesión
                val sesion = sesionRepo.findById(sessionId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        ErrorRes("Sesión no encontrada")
                    )

                if (sesion.usuarioId != usuarioId) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        ErrorRes("No tienes permiso para acceder a esta sesión")
                    )
                }

                // Verificar que no esté ya finalizada
                if (sesion.fechaFin != null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorRes("La sesión ya ha sido finalizada")
                    )
                }

                // Contar preguntas respondidas
                val totalPreguntas = sesionPreguntaRepo.getPreguntasUsadas(sessionId).size

                // Calcular puntaje (mock para MVP)
                val puntaje = when {
                    totalPreguntas == 0 -> 0
                    totalPreguntas <= 2 -> 60
                    totalPreguntas <= 5 -> 75
                    else -> 85
                }

                // Finalizar sesión
                sesionRepo.finalizar(sessionId, puntaje)

                call.respond(
                    ResumenFinalRes(
                        sessionId = sessionId.toString(),
                        puntajeGeneral = puntaje,
                        totalPreguntas = totalPreguntas,
                        observaciones = when {
                            totalPreguntas == 0 -> "No se respondieron preguntas en esta sesión."
                            totalPreguntas <= 2 -> "¡Buen comienzo! Intenta responder más preguntas para mejorar tu práctica."
                            totalPreguntas <= 5 -> "¡Bien hecho! Continúa practicando para fortalecer tus habilidades."
                            else -> "¡Excelente sesión! Has demostrado dedicación en tu práctica."
                        }
                    )
                )
            }
        }
    }
}
