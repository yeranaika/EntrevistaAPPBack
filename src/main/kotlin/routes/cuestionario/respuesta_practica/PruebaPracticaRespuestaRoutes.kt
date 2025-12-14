package routes.cuestionario.respuesta_practica

import com.example.data.tables.IntentoPruebaTable
import data.models.cuestionario.prueba_practica.EnviarRespuestasReq
import data.models.cuestionario.prueba_practica.EnviarRespuestasRes
import data.models.cuestionario.prueba_practica.ResultadoPreguntaRes
import data.models.cuestionario.prueba_practica.RespuestaPreguntaReq
import data.repository.billing.SuscripcionRepository
import data.tables.cuestionario.prueba.PruebaPreguntaTable
import routes.cuestionario.prueba_practica.PruebaTable as PruebaFrontTable
import data.tables.cuestionario.respuestas.RespuestaPruebaTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import services.FeedbackGeneralV2
import services.PracticeGlobalFeedbackService
import services.ResultadoPreguntaResConTexto
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

@Serializable
private data class GetUltimoIntentoRes(
    val pruebaId: String,
    val intentoId: String,
    val feedbackMode: String,
    val puntaje: String? = null,
    val feedbackGeneralV2: FeedbackGeneralV2? = null,
    @SerialName("feedbackGeneralV2Raw")
    val feedbackGeneralV2Raw: String? = null
)

fun Route.pruebaPracticaRespuestaRoutes(
    feedbackService: PracticeGlobalFeedbackService,
    suscripcionRepository: SuscripcionRepository
) {
    val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    authenticate("auth-jwt") {

        /**
         * GET /api/prueba-practica/{pruebaId}/respuestas
         * Devuelve el último intento del usuario (incluye feedback_general_v2).
         */
        get("/api/prueba-practica/{pruebaId}/respuestas") {
            val pruebaIdPath = call.parameters["pruebaId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta pruebaId en la URL")
                )

            val pruebaUuid = try {
                UUID.fromString(pruebaIdPath)
            } catch (_: IllegalArgumentException) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "pruebaId no es un UUID válido")
                )
            }

            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val usuarioId = try {
                UUID.fromString(principal.subject ?: "")
            } catch (_: Exception) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "subject del token no es un UUID válido")
                )
            }

            val row = transaction {
                IntentoPruebaTable
                    .selectAll()
                    .where {
                        (IntentoPruebaTable.usuarioId eq usuarioId) and
                            (IntentoPruebaTable.pruebaId eq pruebaUuid)
                    }
                    .orderBy(IntentoPruebaTable.creadoEn, SortOrder.DESC)
                    .limit(1)
                    .singleOrNull()
            }

            if (row == null) {
                return@get call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No existe un intento para esta prueba y usuario")
                )
            }

            val intentoId = row[IntentoPruebaTable.id].toString()
            val puntaje = row[IntentoPruebaTable.puntaje]?.toString()
            val recomendaciones = row[IntentoPruebaTable.recomendaciones]
            val feedbackMode = if (recomendaciones?.startsWith("feedback_mode:ia") == true) "ia" else "nlp"

            val feedbackJsonStr = row[IntentoPruebaTable.feedbackGeneralV2]

            val feedbackGeneralV2: FeedbackGeneralV2? = runCatching {
                if (feedbackJsonStr.isNullOrBlank()) null
                else json.decodeFromString<FeedbackGeneralV2>(feedbackJsonStr)
            }.getOrNull()

            call.respond(
                GetUltimoIntentoRes(
                    pruebaId = pruebaIdPath,
                    intentoId = intentoId,
                    feedbackMode = feedbackMode,
                    puntaje = puntaje,
                    feedbackGeneralV2 = feedbackGeneralV2,
                    feedbackGeneralV2Raw = feedbackJsonStr
                )
            )
        }

        /**
         * POST /api/prueba-practica/{pruebaId}/respuestas
         * Guarda intento + respuestas y (si aplica) genera feedback IA y lo persiste en JSONB.
         */
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

            var feedbackGeneralV2: FeedbackGeneralV2? = null
            var intentoUuid: UUID? = null
            var pruebaExiste = true

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

                iaRestantes = if (esPremium) Int.MAX_VALUE else maxOf(
                    0,
                    iaRestantesAntes - if (feedbackMode == "ia") 1 else 0
                )

                val rowPrueba = PruebaFrontTable
                    .selectAll()
                    .where { PruebaFrontTable.pruebaId eq pruebaUuid }
                    .limit(1)
                    .singleOrNull()

                if (rowPrueba == null) {
                    pruebaExiste = false
                    return@transaction
                }

                // 2) Cargamos preguntas de esa prueba
                val filas = PruebaPreguntaTable
                    .selectAll()
                    .where { PruebaPreguntaTable.pruebaId eq pruebaUuid }
                    .toList()

                totalPreguntas = filas.size

                val mapaPorPreguntaId: Map<String, ResultRow> =
                    filas.associateBy { row -> row[PruebaPreguntaTable.preguntaId].toString() }

                var buenas = 0

                detalleResultados = req.respuestas.map { r: RespuestaPreguntaReq ->
                    val row = mapaPorPreguntaId[r.preguntaId]

                    val tipo = if (r.opcionesSeleccionadas.isNotEmpty()) "opcion_multiple" else "abierta"

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
                            tipo == "abierta" && !respuestaUsuario.isNullOrBlank()
                        } else {
                            r.opcionesSeleccionadas.size == 1 &&
                                r.opcionesSeleccionadas.first() == clave
                        }

                        if (esCorrecta) buenas++

                        resultadosConTexto += ResultadoPreguntaResConTexto(
                            preguntaId = r.preguntaId,
                            textoPregunta = "Pregunta asociada al ID ${r.preguntaId} (texto no cargado desde la tabla de preguntas en el backend)",
                            correcta = esCorrecta,
                            tipo = tipo,
                            respuestaUsuario = respuestaUsuario
                        )

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

                val puntajeInt = if (totalPreguntas > 0) (correctas * 100) / totalPreguntas else 0

                val newIntentoUuid = UUID.randomUUID()
                intentoUuid = newIntentoUuid

                IntentoPruebaTable.insert {
                    it[IntentoPruebaTable.id] = newIntentoUuid
                    it[IntentoPruebaTable.pruebaId] = pruebaUuid
                    it[IntentoPruebaTable.usuarioId] = usuarioId
                    it[IntentoPruebaTable.fechaInicio] = ahoraStr
                    it[IntentoPruebaTable.fechaFin] = ahoraStr
                    it[IntentoPruebaTable.puntaje] = BigDecimal(puntajeInt)
                    it[IntentoPruebaTable.puntajeTotal] = totalPreguntas
                    it[IntentoPruebaTable.estado] = "finalizado"
                    it[IntentoPruebaTable.tiempoTotalSegundos] = null
                    it[IntentoPruebaTable.recomendaciones] = "feedback_mode:$feedbackMode"
                    it[IntentoPruebaTable.creadoEn] = ahoraStr
                    it[IntentoPruebaTable.actualizadoEn] = ahoraStr
                    it[IntentoPruebaTable.feedbackGeneralV2] = null
                }

                respuestasAGuardar.forEach { r ->
                    RespuestaPruebaTable.insert {
                        // ✅ NO usar intentoUuid (UUID?) acá: Exposed espera UUID no-null
                        it[RespuestaPruebaTable.intentoId] = newIntentoUuid

                        // ✅ En tu Table suele llamarse "preguntaId" aunque la columna sea prueba_pregunta_id
                        it[RespuestaPruebaTable.pruebaPreguntaId] = r.pruebaPreguntaId

                        it[RespuestaPruebaTable.respuestaUsuario] = r.respuestaUsuario
                        it[RespuestaPruebaTable.correcta] = r.correcta
                        it[RespuestaPruebaTable.feedbackInspecl] = null
                    }
                }
            }

            if (!pruebaExiste) {
                return@post call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No existe la prueba solicitada")
                )
            }

            val respondidas = req.respuestas.size
            val puntaje = if (totalPreguntas > 0) (correctas * 100) / totalPreguntas else 0

            val feedbackGeneral = if (feedbackMode == "ia") {
                val v2 = feedbackService.generarFeedbackGeneralV2(
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    correctas = correctas,
                    preguntas = resultadosConTexto
                )
                feedbackGeneralV2 = v2

                val intentoIdParaUpdate = intentoUuid
                if (intentoIdParaUpdate != null) {
                    val jsonStr = json.encodeToString(v2)
                    val safeJson = jsonStr.replace("'", "''")

                    transaction {
                        exec(
                            """
                            UPDATE app.intento_prueba
                            SET feedback_general_v2 = '$safeJson'::jsonb
                            WHERE intento_id = '${intentoIdParaUpdate}'::uuid
                            """.trimIndent()
                        )
                    }
                }

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
                feedbackService.generarFeedbackNlpBasico(
                    puntaje = puntaje,
                    totalPreguntas = totalPreguntas,
                    correctas = correctas,
                    preguntas = resultadosConTexto
                )
            }

            val iaRestantesRespuesta = if (esPremium) null else iaRestantes

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
