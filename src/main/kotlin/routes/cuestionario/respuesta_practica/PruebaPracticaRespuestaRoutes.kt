/* src/main/kotlin/routes/cuestionario/respuesta_practica/PruebaPracticaRespuestaRoutes.kt */

package routes.cuestionario.respuesta_practica

import data.models.cuestionario.prueba_practica.EnviarRespuestasReq
import data.models.cuestionario.prueba_practica.EnviarRespuestasRes
import data.models.cuestionario.prueba_practica.ResultadoPreguntaRes
import data.models.cuestionario.prueba_practica.RespuestaPreguntaReq
import data.repository.billing.SuscripcionRepository
import data.tables.cuestionario.intentos_practica.IntentoPruebaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable

// Tabla ligera usada en las rutas front
import routes.cuestionario.prueba_practica.PruebaTable as PruebaFrontTable

// Tabla real de respuestas
import data.tables.cuestionario.respuestas.RespuestaPruebaTable

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import services.FeedbackGeneralV2
import services.PracticeGlobalFeedbackService
import services.ResultadoPreguntaResConTexto
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Route.pruebaPracticaRespuestaRoutes(
    feedbackService: PracticeGlobalFeedbackService,
    suscripcionRepository: SuscripcionRepository
) {

    authenticate("auth-jwt") {

        post("/api/prueba-practica/{pruebaId}/respuestas") {
            val pruebaIdPath = call.parameters["pruebaId"]
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta pruebaId en la URL")
                )

            val pruebaUuid = try {
                UUID.fromString(pruebaIdPath)
            } catch (_: IllegalArgumentException) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "pruebaId no es un UUID válido")
                )
            }

            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val usuarioId = try {
                UUID.fromString(principal.subject ?: "")
            } catch (_: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "subject del token no es un UUID válido")
                )
            }

            val esPremium = runCatching { suscripcionRepository.getCurrentStatus(usuarioId).isPremium }
                .getOrDefault(false)

            val req: EnviarRespuestasReq = call.receive()

            if (req.pruebaId != pruebaIdPath) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "pruebaId del body no coincide con la URL")
                )
            }

            var detalleResultados: List<ResultadoPreguntaRes> = emptyList()
            var totalPreguntas = 0
            var correctas = 0

            val resultadosConTexto = mutableListOf<ResultadoPreguntaResConTexto>()

            val ahoraStr = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))

            var tipoPruebaEtiqueta: String = "practica"

            // Datos auxiliares para insertar en respuesta_prueba
            data class RespuestaAGuardar(
                val pruebaPreguntaId: UUID,
                val respuestaUsuario: String?,
                val correcta: Boolean
            )
            val respuestasAGuardar = mutableListOf<RespuestaAGuardar>()

            var iaUsosPrevios = 0
            var iaRestantes = 0
            var feedbackMode = "nlp"

            // NUEVO: contenedor del JSON estructurado (solo cuando feedbackMode = "ia")
            var feedbackGeneralV2: FeedbackGeneralV2? = null

            transaction {
                iaUsosPrevios = IntentoPruebaTable
                    .selectAll()
                    .where {
                        (IntentoPruebaTable.usuarioId eq usuarioId) and
                            (IntentoPruebaTable.recomendaciones like "feedback_mode:ia%")
                    }
                    .count()
                    .toInt()

                val iaRestantesAntes = if (esPremium) Int.MAX_VALUE else maxOf(0, 10 - iaUsosPrevios)
                val solicitaIa = req.usarIaFeedback == true
                feedbackMode = when {
                    esPremium -> "ia"
                    solicitaIa && iaRestantesAntes > 0 -> "ia"
                    else -> "nlp"
                }
                iaRestantes = if (esPremium) Int.MAX_VALUE else maxOf(0, iaRestantesAntes - if (feedbackMode == "ia") 1 else 0)

                // 1) Miramos la tabla PRUEBA (front) para ver qué tipo es
                val rowPrueba = PruebaFrontTable
                    .selectAll()
                    .where { PruebaFrontTable.pruebaId eq pruebaUuid }
                    .singleOrNull()

                tipoPruebaEtiqueta = rowPrueba?.get(PruebaFrontTable.tipoPrueba) ?: "practica"

                // 2) Cargamos preguntas de esa prueba
                val filas = PruebaPreguntaTable
                    .selectAll()
                    .where { PruebaPreguntaTable.pruebaId eq pruebaUuid }
                    .toList()

                totalPreguntas = filas.size

                // Mapa preguntaId (pregunta.uuid) -> fila de PRUEBA_PREGUNTA
                val mapaPorPreguntaId: Map<String, ResultRow> =
                    filas.associateBy { row ->
                        row[PruebaPreguntaTable.preguntaId].toString()
                    }

                var buenas = 0

                detalleResultados = req.respuestas.map { r: RespuestaPreguntaReq ->
                    val row = mapaPorPreguntaId[r.preguntaId]

                    val tipo = if (r.opcionesSeleccionadas.isNotEmpty()) {
                        "opcion_multiple"
                    } else {
                        "abierta"
                    }

                    val respuestaUsuario: String? =
                        if (tipo == "opcion_multiple") {
                            if (r.opcionesSeleccionadas.isEmpty()) null
                            else r.opcionesSeleccionadas.joinToString(",")
                        } else {
                            r.textoLibre
                        }

                    if (row == null) {
                        resultadosConTexto += ResultadoPreguntaResConTexto(
                            preguntaId = r.preguntaId,
                            textoPregunta = "Pregunta no encontrada en la base de datos (ID=${r.preguntaId})",
                            correcta = false,
                            tipo = tipo,
                            respuestaUsuario = respuestaUsuario
                        )

                        ResultadoPreguntaRes(
                            preguntaId = r.preguntaId,
                            correcta = false,
                            claveCorrecta = null,
                            seleccionadas = r.opcionesSeleccionadas
                        )
                    } else {
                        val clave = row[PruebaPreguntaTable.claveCorrecta]

                        val esCorrecta = if (clave.isNullOrBlank()) {
                            // abiertas: de momento solo marcamos como "buena" si respondió algo
                            tipo == "abierta" && !respuestaUsuario.isNullOrBlank()
                        } else {
                            r.opcionesSeleccionadas.size == 1 &&
                                r.opcionesSeleccionadas.first() == clave
                        }

                        if (esCorrecta) buenas++

                        val textoPregunta =
                            "Pregunta asociada al ID ${r.preguntaId} (texto no cargado desde la tabla de preguntas en el backend)"

                        resultadosConTexto += ResultadoPreguntaResConTexto(
                            preguntaId = r.preguntaId,
                            textoPregunta = textoPregunta,
                            correcta = esCorrecta,
                            tipo = tipo,
                            respuestaUsuario = respuestaUsuario
                        )

                        // 🔹 Guardamos datos para respuesta_prueba
                        val pruebaPreguntaId = row[PruebaPreguntaTable.id]
                        respuestasAGuardar += RespuestaAGuardar(
                            pruebaPreguntaId = pruebaPreguntaId,
                            respuestaUsuario = respuestaUsuario,
                            correcta = esCorrecta
                        )

                        ResultadoPreguntaRes(
                            preguntaId = r.preguntaId,
                            correcta = esCorrecta,
                            claveCorrecta = clave,
                            seleccionadas = r.opcionesSeleccionadas
                        )
                    }
                }

                correctas = buenas

                val puntajeInt = if (totalPreguntas > 0) {
                    (correctas * 100) / totalPreguntas
                } else 0

                // 3) Crear el INTENTO_PRUEBA
                val intentoUuid = UUID.randomUUID()

                IntentoPruebaTable.insert {
                    it[IntentoPruebaTable.intentoId] = intentoUuid
                    it[IntentoPruebaTable.pruebaId] = pruebaUuid
                    it[IntentoPruebaTable.usuarioIdCol] = usuarioId
                    it[IntentoPruebaTable.fechaInicio] = ahoraStr
                    it[IntentoPruebaTable.fechaFin] = ahoraStr
                    it[IntentoPruebaTable.puntaje] = BigDecimal(puntajeInt)
                    it[IntentoPruebaTable.puntajeTotal] = totalPreguntas
                    it[IntentoPruebaTable.estado] = "finalizado"
                    it[IntentoPruebaTable.tiempoTotalSegundos] = null
                    it[IntentoPruebaTable.recomendaciones] = "feedback_mode:$feedbackMode"
                    it[IntentoPruebaTable.creadoEn] = ahoraStr
                    it[IntentoPruebaTable.actualizadoEn] = ahoraStr
                }

                // 4) Insertar cada respuesta en RESPUESTA_PRUEBA
                respuestasAGuardar.forEach { r ->
                    RespuestaPruebaTable.insert {
                        it[RespuestaPruebaTable.intentoId] = intentoUuid
                        // 👇 CAMBIO IMPORTANTE: usamos la columna nueva
                        it[RespuestaPruebaTable.pruebaPreguntaId] = r.pruebaPreguntaId
                        it[RespuestaPruebaTable.respuestaUsuario] = r.respuestaUsuario
                        it[RespuestaPruebaTable.correcta] = r.correcta
                        it[RespuestaPruebaTable.feedbackInspecl] = null
                    }
                }
            }

            val respondidas = req.respuestas.size
            val puntaje = if (totalPreguntas > 0) (correctas * 100) / totalPreguntas else 0

            val feedbackGeneral = if (feedbackMode == "ia") {
                // NUEVO: generamos JSON estructurado y mantenemos texto legacy
                val v2 = feedbackService.generarFeedbackGeneralV2(
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    correctas = correctas,
                    preguntas = resultadosConTexto
                )
                feedbackGeneralV2 = v2

                // Texto legacy (compatibilidad) a partir del JSON (sin "churrullo")
                buildString {
                    appendLine(v2.summary.oneLiner)
                    appendLine()
                    v2.sections.forEach { sec ->
                        appendLine("${sec.title}:")
                        sec.bullets.forEach { b -> appendLine("- $b") }
                        appendLine()
                    }
                }.trim()
            } else {
                // NLP: NO tocar
                feedbackService.generarFeedbackNlpBasico(
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    correctas = correctas,
                    preguntas = resultadosConTexto
                )
            }

            val iaRestantesRespuesta = if (esPremium) null else iaRestantes

            // OJO: debes agregar `feedbackGeneralV2` al DTO EnviarRespuestasRes
            val res = EnviarRespuestasRes(
                pruebaId = pruebaIdPath,
                totalPreguntas = totalPreguntas,
                respondidas = respondidas,
                correctas = correctas,
                puntaje = puntaje,
                detalle = detalleResultados,
                feedbackGeneral = feedbackGeneral,
                feedbackGeneralV2 = feedbackGeneralV2,
                feedbackMode = feedbackMode,
                iaRevisionesRestantes = iaRestantesRespuesta
            )

            call.respond(res)
        }
    }
}
