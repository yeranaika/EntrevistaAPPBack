package routes.nivelacion

import data.models.*
import data.repository.nivelacion.PreguntaNivelacionRepository
import data.repository.nivelacion.TestNivelacionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID
import plugins.settings
import io.ktor.serialization.kotlinx.json.json

fun Route.testNivelacionRoutes(
    preguntaRepo: PreguntaNivelacionRepository,
    testRepo: TestNivelacionRepository
) {
    authenticate("auth-jwt") {
        route("/tests/nivelacion") {

            // GET /tests/nivelacion?habilidad=logica&cantidad=10
            // Obtiene un test de nivelación con preguntas aleatorias
            get {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val habilidad = call.request.queryParameters["habilidad"]
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Parámetro 'habilidad' requerido")
                    )

                val cantidad = call.request.queryParameters["cantidad"]?.toIntOrNull() ?: 10

                // Validar que haya suficientes preguntas
                val disponibles = preguntaRepo.countByHabilidad(habilidad)
                if (disponibles < cantidad) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "No hay suficientes preguntas disponibles",
                            "disponibles" to disponibles,
                            "solicitadas" to cantidad
                        )
                    )
                }

                // Obtener preguntas aleatorias
                val preguntas = preguntaRepo.findRandomByHabilidad(habilidad, cantidad)

                val response = TestNivelacionRes(
                    habilidad = habilidad,
                    preguntas = preguntas.map { pregunta ->
                        PreguntaNivelacionRes(
                            id = pregunta.id.toString(),
                            enunciado = pregunta.enunciado,
                            opciones = pregunta.opciones,
                            dificultad = pregunta.dificultad
                        )
                    },
                    totalPreguntas = preguntas.size
                )

                call.respond(HttpStatusCode.OK, response)
            }

            // POST /tests/nivelacion/responder
            // Evalúa las respuestas del usuario y genera resultado + feedback
            post("/responder") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()
                val request = call.receive<ResponderTestReq>()

                if (request.respuestas.isEmpty()) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "No se enviaron respuestas")
                    )
                }

                // Obtener las preguntas del test
                val preguntaIds = request.respuestas.map { UUID.fromString(it.preguntaId) }
                val preguntas = preguntaRepo.findByIds(preguntaIds)

                if (preguntas.size != request.respuestas.size) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Algunas preguntas no fueron encontradas")
                    )
                }

                // Crear mapa de preguntas por ID para fácil acceso
                val preguntasMap = preguntas.associateBy { it.id }

                // Evaluar respuestas
                val detalles = mutableListOf<DetalleRespuesta>()
                var correctas = 0

                for (respuesta in request.respuestas) {
                    val preguntaId = UUID.fromString(respuesta.preguntaId)
                    val pregunta = preguntasMap[preguntaId] ?: continue

                    val esCorrecta = pregunta.respuestaCorrecta == respuesta.respuestaSeleccionada
                    if (esCorrecta) correctas++

                    detalles.add(
                        DetalleRespuesta(
                            preguntaId = pregunta.id.toString(),
                            enunciado = pregunta.enunciado,
                            respuestaUsuario = respuesta.respuestaSeleccionada,
                            respuestaCorrecta = pregunta.respuestaCorrecta,
                            esCorrecta = esCorrecta,
                            explicacion = pregunta.explicacion
                        )
                    )
                }

                // Calcular puntaje (0-100)
                val totalPreguntas = request.respuestas.size
                val puntaje = (correctas * 100) / totalPreguntas

                // Determinar nivel sugerido y feedback
                val (nivelSugerido, nivelTexto, feedback) = calcularNivelYFeedback(
                    puntaje,
                    correctas,
                    totalPreguntas,
                    request.habilidad
                )

                // Guardar resultado en historial
                val testId = testRepo.create(
                    usuarioId = userId,
                    habilidad = request.habilidad,
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    preguntasCorrectas = correctas,
                    nivelSugerido = nivelSugerido,
                    feedback = feedback
                )

                val response = ResultadoTestRes(
                    testId = testId.toString(),
                    habilidad = request.habilidad,
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    preguntasCorrectas = correctas,
                    nivelSugerido = nivelTexto,
                    feedback = feedback,
                    detalleRespuestas = detalles
                )

                call.respond(HttpStatusCode.OK, response)
            }

            // GET /tests/nivelacion/historial
            // Obtiene el historial de tests del usuario autenticado
            get("/historial") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()
                val habilidad = call.request.queryParameters["habilidad"]

                val tests = if (habilidad != null) {
                    testRepo.findByUsuarioAndHabilidad(userId, habilidad)
                } else {
                    testRepo.findByUsuario(userId)
                }

                val response = tests.map { test ->
                    HistorialTestRes(
                        id = test.id.toString(),
                        habilidad = test.habilidad,
                        puntaje = test.puntaje,
                        nivelSugerido = nivelNumericoATexto(test.nivelSugerido),
                        fechaCompletado = test.fechaCompletado.toString()
                    )
                }

                call.respond(HttpStatusCode.OK, response)
            }

            // GET /tests/nivelacion/{testId}
            // Obtiene el detalle de un test específico
            get("/{testId}") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val userId = principal.userIdFromJwt()
                val testId = call.parameters["testId"]?.let { UUID.fromString(it) }
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "ID de test inválido")
                    )

                val test = testRepo.findById(testId)
                    ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Test no encontrado")
                    )

                // Verificar que el test pertenece al usuario
                if (test.usuarioId != userId) {
                    return@get call.respond(HttpStatusCode.Forbidden)
                }

                val response = HistorialTestRes(
                    id = test.id.toString(),
                    habilidad = test.habilidad,
                    puntaje = test.puntaje,
                    nivelSugerido = nivelNumericoATexto(test.nivelSugerido),
                    fechaCompletado = test.fechaCompletado.toString()
                )

                call.respond(HttpStatusCode.OK, response)
            }

            // POST /tests/nivelacion/generate-from-job
            // Genera un test de nivelación basado en un aviso de trabajo
            post("/generate-from-job") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val req = try {
                    call.receive<GenerateFromJobReq>()
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Body inválido"))
                }

                val job = req.job
                val cantidad = req.cantidad ?: 5
                
                // Usamos el ID externo del job como clave de habilidad
                val habilidadKey = "JOB:${job.idExterno}"

                // Verificar si ya existen preguntas para este job
                val existentes = preguntaRepo.countByHabilidad(habilidadKey)
                
                if (existentes < cantidad) {
                    // Generar nuevas preguntas con IA
                    val generated = services.InterviewQuestionService(
                        io.ktor.client.HttpClient { 
                            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                            }
                        }, 
                        application.settings().openAiApiKey
                    ).generateMultipleChoiceQuestions(job, cantidad)

                    // Guardar en BD
                    generated.forEach { q ->
                        preguntaRepo.create(
                            habilidad = habilidadKey,
                            dificultad = q.dificultad,
                            enunciado = q.enunciado,
                            opciones = q.opciones,
                            respuestaCorrecta = q.respuestaCorrecta,
                            explicacion = q.explicacion
                        )
                    }
                }

                call.respond(GenerateFromJobRes(
                    message = "Test generado exitosamente",
                    habilidad = habilidadKey,
                    cantidadPreguntas = preguntaRepo.countByHabilidad(habilidadKey)
                ))
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class GenerateFromJobRes(
    val message: String,
    val habilidad: String,
    val cantidadPreguntas: Long
)

@kotlinx.serialization.Serializable
data class GenerateFromJobReq(
    val job: services.JobNormalizedDto,
    val cantidad: Int? = 5
)

// Función auxiliar para extraer userId del JWT
private fun JWTPrincipal.userIdFromJwt(): UUID {
    val sub = this.payload.getClaim("sub").asString()
    return UUID.fromString(sub)
}

// Función para calcular nivel y feedback basado en el puntaje
private fun calcularNivelYFeedback(
    puntaje: Int,
    correctas: Int,
    total: Int,
    habilidad: String
): Triple<Int, String, String> {
    return when {
        puntaje >= 80 -> Triple(
            3,
            "avanzado",
            "¡Excelente trabajo! Has demostrado un dominio avanzado en $habilidad. " +
            "Respondiste correctamente $correctas de $total preguntas ($puntaje%). " +
            "Estás listo para enfrentar desafíos complejos en esta área."
        )
        puntaje >= 60 -> Triple(
            2,
            "intermedio",
            "¡Buen trabajo! Tienes un nivel intermedio en $habilidad. " +
            "Respondiste correctamente $correctas de $total preguntas ($puntaje%). " +
            "Con un poco más de práctica, podrás alcanzar el nivel avanzado."
        )
        else -> Triple(
            1,
            "básico",
            "Has completado el test de $habilidad. " +
            "Respondiste correctamente $correctas de $total preguntas ($puntaje%). " +
            "Te recomendamos reforzar los conceptos básicos en esta área antes de avanzar."
        )
    }
}

// Función para convertir nivel numérico a texto
private fun nivelNumericoATexto(nivel: Int): String {
    return when (nivel) {
        1 -> "básico"
        2 -> "intermedio"
        3 -> "avanzado"
        else -> "desconocido"
    }
}
