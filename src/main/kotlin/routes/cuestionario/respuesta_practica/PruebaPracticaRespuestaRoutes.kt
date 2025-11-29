package routes.cuestionario.respuesta_practica

import data.models.cuestionario.prueba_practica.EnviarRespuestasReq
import data.models.cuestionario.prueba_practica.EnviarRespuestasRes
import data.models.cuestionario.prueba_practica.ResultadoPreguntaRes
import data.models.cuestionario.prueba_practica.RespuestaPreguntaReq

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import routes.cuestionario.prueba_practica.PruebaPreguntaTable
import java.util.UUID

/**
 * Rutas para recibir y corregir respuestas de la prueba práctica.
 */
fun Route.pruebaPracticaRespuestaRoutes() {

    post("/api/prueba-practica/{pruebaId}/respuestas") {
        // 1) Validar pruebaId en la URL
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

        // 2) Leer body (DTO que ya definiste en data.models)
        val req: EnviarRespuestasReq = call.receive()

        // (opcional) validar que body y URL coincidan
        if (req.pruebaId != pruebaIdPath) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "pruebaId del body no coincide con la URL")
            )
        }

        // 3) Procesar en la BD
        var detalleResultados: List<ResultadoPreguntaRes> = emptyList()
        var totalPreguntas = 0
        var correctas = 0

        transaction {
            // Traemos las preguntas de esta prueba con su clave_correcta
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

                if (row == null) {
                    // Pregunta que no pertenece a esta prueba → incorrecta
                    ResultadoPreguntaRes(
                        preguntaId = r.preguntaId,
                        correcta = false,
                        claveCorrecta = null,
                        seleccionadas = r.opcionesSeleccionadas
                    )
                } else {
                    val clave = row[PruebaPreguntaTable.claveCorrecta]  // puede ser null

                    // Por ahora soportamos selección única
                    val esCorrecta = if (clave.isNullOrBlank()) {
                        false
                    } else {
                        r.opcionesSeleccionadas.size == 1 &&
                                r.opcionesSeleccionadas.first() == clave
                    }

                    if (esCorrecta) buenas++

                    ResultadoPreguntaRes(
                        preguntaId = r.preguntaId,
                        correcta = esCorrecta,
                        claveCorrecta = clave,
                        seleccionadas = r.opcionesSeleccionadas
                    )
                }
            }

            correctas = buenas
        }

        val respondidas = req.respuestas.size
        val puntaje = if (totalPreguntas > 0) (correctas * 100) / totalPreguntas else 0

        val res = EnviarRespuestasRes(
            pruebaId = pruebaIdPath,
            totalPreguntas = totalPreguntas,
            respondidas = respondidas,
            correctas = correctas,
            puntaje = puntaje,
            detalle = detalleResultados
        )

        // 4) Devolver resultado al front
        call.respond(res)
    }
}
