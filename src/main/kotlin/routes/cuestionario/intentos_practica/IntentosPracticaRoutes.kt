/* src/main/kotlin/routes/cuestionario/intentos_practica/IntentosPracticaRoutes.kt */

package routes.cuestionario.intentos_practica

import com.example.data.tables.IntentoPruebaTable as IntentoPruebaAppTable
import data.models.cuestionario.intentos_practica.HistorialPracticaItemRes
import data.models.cuestionario.prueba_practica.IntentoDetallePreguntaRes
import data.models.cuestionario.prueba_practica.IntentoDetalleRes
import data.tables.cuestionario.intentos_practica.IntentoPruebaTable as IntentoPracticaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable
import data.tables.cuestionario.prueba.PruebaTable
import data.tables.cuestionario.preguntas.PreguntaTable as PreguntaDbTable
import data.tables.cuestionario.respuestas.RespuestaPruebaTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import services.FeedbackGeneralV2
import services.PracticeGlobalFeedbackService
import services.ResultadoPreguntaResConTexto
import java.util.UUID

fun Route.intentosPracticaRoutes(
    feedbackService: PracticeGlobalFeedbackService? = null
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    authenticate("auth-jwt") {

        // =========================
        // LISTADO HISTORIAL (tu ruta actual)
        // =========================
        get("/api/prueba-practica/intentos") {
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

            val filtroTipoPrueba = call.request.queryParameters["tipoPrueba"]
                ?.normalizaTipoPrueba()

            val intentos = transaction {
                // Intentos registrados en la app (nivelación, entrevista, etc.)
                val intentosApp = IntentoPruebaAppTable
                    .join(PruebaTable, JoinType.INNER) { IntentoPruebaAppTable.pruebaId eq PruebaTable.pruebaId }
                    .selectAll()
                    .andWhere { IntentoPruebaAppTable.usuarioId eq usuarioId }
                    .map { row ->
                        IntentoRow(
                            intentoId = row[IntentoPruebaAppTable.id].toString(),
                            pruebaId = row[IntentoPruebaAppTable.pruebaId].toString(),
                            tipoPrueba = row[PruebaTable.tipoPrueba].normalizaTipoPrueba(),
                            fechaFin = row[IntentoPruebaAppTable.fechaFin],
                            puntaje = row[IntentoPruebaAppTable.puntaje]?.toInt(),
                            puntajeTotal = row[IntentoPruebaAppTable.puntajeTotal],
                            estado = row[IntentoPruebaAppTable.estado],
                            creadoEn = row[IntentoPruebaAppTable.creadoEn]
                        )
                    }

                // Intentos propios de práctica (historial antiguo)
                val intentosPractica = IntentoPracticaTable
                    .join(PruebaTable, JoinType.INNER) { IntentoPracticaTable.pruebaId eq PruebaTable.pruebaId }
                    .selectAll()
                    .andWhere { IntentoPracticaTable.usuarioIdCol eq usuarioId }
                    .map { row ->
                        IntentoRow(
                            intentoId = row[IntentoPracticaTable.intentoId].toString(),
                            pruebaId = row[IntentoPracticaTable.pruebaId].toString(),
                            tipoPrueba = row[PruebaTable.tipoPrueba].normalizaTipoPrueba(),
                            fechaFin = row[IntentoPracticaTable.fechaFin],
                            puntaje = row[IntentoPracticaTable.puntaje]?.toInt(),
                            puntajeTotal = row[IntentoPracticaTable.puntajeTotal],
                            estado = row[IntentoPracticaTable.estado],
                            creadoEn = row[IntentoPracticaTable.creadoEn]
                        )
                    }

                (intentosApp + intentosPractica)
                    .let { lista ->
                        filtroTipoPrueba?.let { tipo ->
                            lista.filter { it.tipoPrueba == tipo }
                        } ?: lista
                    }
                    .sortedByDescending { it.creadoEn }
                    .distinctBy { it.intentoId }
                    .take(20)
                    .map { intento ->
                        HistorialPracticaItemRes(
                            intentoId = intento.intentoId,
                            pruebaId = intento.pruebaId,
                            tipoPrueba = intento.tipoPrueba,
                            fechaFin = intento.fechaFin,
                            puntaje = intento.puntaje,
                            puntajeTotal = intento.puntajeTotal,
                            estado = intento.estado
                        )
                    }
            }

            call.respond(intentos)
        }

        // =========================
        // DETALLE POR INTENTO (NUEVO)
        // =========================
        get("/api/prueba-practica/intentos/{intentoId}") {
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

            val intentoIdPath = call.parameters["intentoId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta intentoId"))

            val intentoUuid = try {
                UUID.fromString(intentoIdPath)
            } catch (_: Exception) {
                return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "intentoId no es un UUID válido"))
            }

            // 1) Primero intentamos buscarlo en app.intento_prueba (donde guardas feedback_general_v2)
            val detalleApp: IntentoDetalleRes? = transaction {
                val intentoRow = IntentoPruebaAppTable
                    .join(PruebaTable, JoinType.INNER) { IntentoPruebaAppTable.pruebaId eq PruebaTable.pruebaId }
                    .selectAll()
                    .andWhere { IntentoPruebaAppTable.usuarioId eq usuarioId }
                    .andWhere { IntentoPruebaAppTable.id eq intentoUuid }
                    .limit(1)
                    .singleOrNull()
                    ?: return@transaction null

                val pruebaId = intentoRow[IntentoPruebaAppTable.pruebaId].toString()
                val tipoPrueba = intentoRow[PruebaTable.tipoPrueba].normalizaTipoPrueba()

                val recomendaciones = intentoRow[IntentoPruebaAppTable.recomendaciones]
                val feedbackMode = if (recomendaciones?.startsWith("feedback_mode:ia") == true) "ia" else "nlp"

                val feedbackJsonStr = intentoRow[IntentoPruebaAppTable.feedbackGeneralV2]
                val feedbackV2: FeedbackGeneralV2? = runCatching {
                    if (feedbackJsonStr.isNullOrBlank()) null
                    else json.decodeFromString<FeedbackGeneralV2>(feedbackJsonStr)
                }.getOrNull()

                // 2) Traemos respuestas + texto + orden + clave_correcta
                val respRows = RespuestaPruebaTable
                    .join(PruebaPreguntaTable, JoinType.INNER) { RespuestaPruebaTable.pruebaPreguntaId eq PruebaPreguntaTable.id }
                    .join(PreguntaDbTable, JoinType.INNER) { PruebaPreguntaTable.preguntaId eq PreguntaDbTable.id }
                    .selectAll()
                    .andWhere { RespuestaPruebaTable.intentoId eq intentoUuid }
                    .orderBy(PruebaPreguntaTable.orden, SortOrder.ASC)
                    .toList()

                val respuestas = respRows.map { row ->
                    IntentoDetallePreguntaRes(
                        preguntaId = row[PruebaPreguntaTable.preguntaId].toString(),
                        pruebaPreguntaId = row[PruebaPreguntaTable.id].toString(),
                        orden = row[PruebaPreguntaTable.orden],
                        tipoPregunta = row[PreguntaDbTable.tipoPregunta],
                        texto = row[PreguntaDbTable.texto],
                        respuestaUsuario = row[RespuestaPruebaTable.respuestaUsuario],
                        correcta = row[RespuestaPruebaTable.correcta],
                        claveCorrecta = row[PruebaPreguntaTable.claveCorrecta]
                    )
                }

                val respondidas = respuestas.count { !it.respuestaUsuario.isNullOrBlank() }
                val correctas = respuestas.count { it.correcta == true }

                val puntajeInt = intentoRow[IntentoPruebaAppTable.puntaje]?.toInt()
                val puntajeTotal = intentoRow[IntentoPruebaAppTable.puntajeTotal]
                val estado = intentoRow[IntentoPruebaAppTable.estado]
                val fechaFin = intentoRow[IntentoPruebaAppTable.fechaFin]

                val feedbackGeneralStr: String? =
                    when {
                        feedbackV2 != null -> feedbackV2.toPlainText()
                        feedbackMode == "nlp" && feedbackService != null -> {
                            val preguntasConTexto = respuestas.map { r ->
                                ResultadoPreguntaResConTexto(
                                    preguntaId = r.preguntaId,
                                    textoPregunta = r.texto,
                                    correcta = (r.correcta == true),
                                    tipo = r.tipoPregunta,
                                    respuestaUsuario = r.respuestaUsuario
                                )
                            }
                            val p = puntajeInt ?: if (puntajeTotal > 0) (correctas * 100) / puntajeTotal else 0
                            feedbackService.generarFeedbackNlpBasico(
                                puntaje = p,
                                totalPreguntas = puntajeTotal,
                                correctas = correctas,
                                preguntas = preguntasConTexto
                            )
                        }
                        else -> null
                    }

                IntentoDetalleRes(
                    intentoId = intentoIdPath,
                    pruebaId = pruebaId,
                    tipoPrueba = tipoPrueba,
                    fechaFin = fechaFin,
                    puntaje = puntajeInt,
                    puntajeTotal = puntajeTotal,
                    correctas = correctas,
                    respondidas = respondidas,
                    estado = estado,
                    feedbackMode = feedbackMode,
                    feedbackGeneral = feedbackGeneralStr,
                    feedbackGeneralV2 = feedbackV2,
                    respuestas = respuestas
                )
            }

            if (detalleApp != null) {
                return@get call.respond(detalleApp)
            }

            // 2) Si no está en app.intento_prueba, probamos en el historial antiguo
            val detalleAntiguo: IntentoDetalleRes? = transaction {
                val oldRow = IntentoPracticaTable
                    .join(PruebaTable, JoinType.INNER) { IntentoPracticaTable.pruebaId eq PruebaTable.pruebaId }
                    .selectAll()
                    .andWhere { IntentoPracticaTable.usuarioIdCol eq usuarioId }
                    .andWhere { IntentoPracticaTable.intentoId eq intentoUuid }
                    .limit(1)
                    .singleOrNull()
                    ?: return@transaction null

                IntentoDetalleRes(
                    intentoId = intentoIdPath,
                    pruebaId = oldRow[IntentoPracticaTable.pruebaId].toString(),
                    tipoPrueba = oldRow[PruebaTable.tipoPrueba].normalizaTipoPrueba(),
                    fechaFin = oldRow[IntentoPracticaTable.fechaFin],
                    puntaje = oldRow[IntentoPracticaTable.puntaje]?.toInt(),
                    puntajeTotal = oldRow[IntentoPracticaTable.puntajeTotal],
                    correctas = 0,
                    respondidas = 0,
                    estado = oldRow[IntentoPracticaTable.estado],
                    feedbackMode = "nlp",
                    feedbackGeneral = null,
                    feedbackGeneralV2 = null,
                    respuestas = emptyList()
                )
            }

            if (detalleAntiguo != null) {
                return@get call.respond(detalleAntiguo)
            }

            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to "Intento no encontrado (o no pertenece al usuario)")
            )
        }
    }
}

/**
 * Los registros históricos antiguos pueden tener valores como "nivel" o "blended".
 * Para que el cliente filtre correctamente, los normalizamos a "nivelacion",
 * "practica" o "entrevista".
 */
private fun String.normalizaTipoPrueba(): String = when (this.lowercase()) {
    "nivel", "nivelacion", "nv" -> "nivelacion"
    "blended", "ent", "entrenamiento", "entrevista", "mix" -> "entrevista"
    "pr", "practica", "practice" -> "practica"
    else -> "practica"
}

private data class IntentoRow(
    val intentoId: String,
    val pruebaId: String,
    val tipoPrueba: String,
    val fechaFin: String?,
    val puntaje: Int?,
    val puntajeTotal: Int,
    val estado: String,
    val creadoEn: String
)

private fun FeedbackGeneralV2.toPlainText(): String = buildString {
    appendLine(this@toPlainText.summary.oneLiner)
    appendLine()
    this@toPlainText.sections.forEach { sec ->
        appendLine("${sec.title}:")
        sec.bullets.forEach { b -> appendLine("- $b") }
        appendLine()
    }
}.trim()
