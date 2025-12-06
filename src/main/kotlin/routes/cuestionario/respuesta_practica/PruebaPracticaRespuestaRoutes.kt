package routes.cuestionario.respuesta_practica

import data.models.cuestionario.prueba_practica.EnviarRespuestasReq
import data.models.cuestionario.prueba_practica.EnviarRespuestasRes
import data.models.cuestionario.prueba_practica.ResultadoPreguntaRes
import data.models.cuestionario.prueba_practica.RespuestaPreguntaReq
import data.tables.cuestionario.intentos_practica.IntentoPruebaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import services.PracticeGlobalFeedbackService
import services.ResultadoPreguntaResConTexto
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

fun Route.pruebaPracticaRespuestaRoutes(
    feedbackService: PracticeGlobalFeedbackService
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

            // Lista auxiliar para construir feedback por tema (incluye texto de la pregunta y respuesta del usuario)
            val resultadosConTexto = mutableListOf<ResultadoPreguntaResConTexto>()

            val ahoraStr = OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))

            transaction {
                val filas = PruebaPreguntaTable
                    .selectAll()
                    .where { PruebaPreguntaTable.pruebaId eq pruebaUuid }
                    .toList()

                totalPreguntas = filas.size

                val mapaPorPreguntaId: Map<String, ResultRow> =
                    filas.associateBy { row ->
                        row[PruebaPreguntaTable.preguntaId].toString()
                    }

                var buenas = 0

                detalleResultados = req.respuestas.map { r: RespuestaPreguntaReq ->
                    val row = mapaPorPreguntaId[r.preguntaId]

                    val textoPregunta: String =
                        if (row == null) {
                            "Pregunta no encontrada en la base de datos (ID=${r.preguntaId})"
                        } else {
                            // TODO: reemplazar con la columna real de texto de la pregunta cuando hagas el join.
                            // Por ahora dejamos un placeholder legible.
                            "Pregunta asociada al ID ${r.preguntaId} (texto no cargado desde la tabla de preguntas en el backend)"
                        }

                    // Detectamos tipo según lo que viene de la respuesta
                    val tipo = if (r.opcionesSeleccionadas.isNotEmpty()) {
                        "opcion_multiple"
                    } else {
                        "abierta"
                    }

                    val respuestaUsuario: String? =
                        if (tipo == "opcion_multiple") {
                            if (r.opcionesSeleccionadas.isEmpty()) null
                            else "Seleccionaste: ${r.opcionesSeleccionadas.joinToString(", ")}"
                        } else {
                            r.textoLibre
                        }

                    if (row == null) {
                        resultadosConTexto += ResultadoPreguntaResConTexto(
                            preguntaId = r.preguntaId,
                            textoPregunta = textoPregunta,
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
                            // Para preguntas abiertas aún no hay corrección automática.
                            // Puedes extender esto más adelante usando IA para corregir texto.
                            tipo == "abierta" && !respuestaUsuario.isNullOrBlank()
                        } else {
                            r.opcionesSeleccionadas.size == 1 &&
                                r.opcionesSeleccionadas.first() == clave
                        }

                        if (esCorrecta) buenas++

                        resultadosConTexto += ResultadoPreguntaResConTexto(
                            preguntaId = r.preguntaId,
                            textoPregunta = textoPregunta,
                            correcta = esCorrecta,
                            tipo = tipo,
                            respuestaUsuario = respuestaUsuario
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

                IntentoPruebaTable.insert {
                    it[IntentoPruebaTable.intentoId] = UUID.randomUUID()
                    it[IntentoPruebaTable.pruebaId] = pruebaUuid
                    it[IntentoPruebaTable.usuarioIdCol] = usuarioId
                    it[IntentoPruebaTable.fechaInicio] = ahoraStr
                    it[IntentoPruebaTable.fechaFin] = ahoraStr
                    it[IntentoPruebaTable.puntaje] = BigDecimal(puntajeInt)
                    it[IntentoPruebaTable.puntajeTotal] = totalPreguntas
                    it[IntentoPruebaTable.estado] = "finalizado"
                    it[IntentoPruebaTable.tiempoTotalSegundos] = null
                    it[IntentoPruebaTable.recomendaciones] = null
                    it[IntentoPruebaTable.creadoEn] = ahoraStr
                    it[IntentoPruebaTable.actualizadoEn] = ahoraStr
                }
            }

            val respondidas = req.respuestas.size
            val puntaje = if (totalPreguntas > 0) (correctas * 100) / totalPreguntas else 0

            // ⬇️ Generamos el feedback general usando IA (OpenAI) a través de PracticeGlobalFeedbackService
            val feedbackGeneral = feedbackService.generarFeedbackGeneral(
                puntaje = puntaje,
                totalPreguntas = totalPreguntas,
                correctas = correctas,
                preguntas = resultadosConTexto
            )

            val res = EnviarRespuestasRes(
                pruebaId = pruebaIdPath,
                totalPreguntas = totalPreguntas,
                respondidas = respondidas,
                correctas = correctas,
                puntaje = puntaje,
                detalle = detalleResultados,
                feedbackGeneral = feedbackGeneral
            )

            call.respond(res)
        }
    }
}
