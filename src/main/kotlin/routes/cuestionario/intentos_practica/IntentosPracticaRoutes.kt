/* src/main/kotlin/routes/cuestionario/intentos_practica/IntentosPracticaRoutes.kt */

package routes.cuestionario.intentos_practica

import com.example.data.tables.IntentoPruebaTable as IntentoPruebaAppTable
import data.models.cuestionario.intentos_practica.HistorialPracticaItemRes
import data.tables.cuestionario.intentos_practica.IntentoPruebaTable as IntentoPracticaTable
import data.tables.cuestionario.prueba.PruebaTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

fun Route.intentosPracticaRoutes() {

    authenticate("auth-jwt") {

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

            val intentos = transaction {
                // Intentos registrados en la app (nivelación, entrevista, etc.)
                val intentosApp = IntentoPruebaAppTable
                    .innerJoin(PruebaTable, { IntentoPruebaAppTable.pruebaId }, { PruebaTable.pruebaId })
                    .selectAll()
                    .andWhere { IntentoPruebaAppTable.usuarioId eq usuarioId }
                    .map { row ->
                        IntentoRow(
                            intentoId = row[IntentoPruebaAppTable.id].toString(),
                            pruebaId = row[IntentoPruebaAppTable.pruebaId].toString(),
                            tipoPrueba = row[PruebaTable.tipoPrueba],
                            fechaFin = row[IntentoPruebaAppTable.fechaFin],
                            puntaje = row[IntentoPruebaAppTable.puntaje]?.toInt(),
                            puntajeTotal = row[IntentoPruebaAppTable.puntajeTotal],
                            estado = row[IntentoPruebaAppTable.estado],
                            creadoEn = row[IntentoPruebaAppTable.creadoEn]
                        )
                    }

                // Intentos propios de práctica
                val intentosPractica = IntentoPracticaTable
                    .innerJoin(PruebaTable, { IntentoPracticaTable.pruebaId }, { PruebaTable.pruebaId })
                    .selectAll()
                    .andWhere { IntentoPracticaTable.usuarioIdCol eq usuarioId }
                    .map { row ->
                        IntentoRow(
                            intentoId = row[IntentoPracticaTable.intentoId].toString(),
                            pruebaId = row[IntentoPracticaTable.pruebaId].toString(),
                            tipoPrueba = row[PruebaTable.tipoPrueba],
                            fechaFin = row[IntentoPracticaTable.fechaFin],
                            puntaje = row[IntentoPracticaTable.puntaje]?.toInt(),
                            puntajeTotal = row[IntentoPracticaTable.puntajeTotal],
                            estado = row[IntentoPracticaTable.estado],
                            creadoEn = row[IntentoPracticaTable.creadoEn]
                        )
                    }

                (intentosApp + intentosPractica)
                    .sortedByDescending { intento -> intento.creadoEn }
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
    }
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
