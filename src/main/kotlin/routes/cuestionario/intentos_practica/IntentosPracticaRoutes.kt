/* src/main/kotlin/routes/cuestionario/intentos_practica/IntentosPracticaRoutes.kt */

package routes.cuestionario.intentos_practica

import data.tables.cuestionario.intentos_practica.IntentoPruebaTable
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

@Serializable
data class IntentoPracticaRes(
    val intentoId: String,
    val pruebaId: String,
    val fechaInicio: String,
    val fechaFin: String?,
    val puntaje: Int?,
    val puntajeTotal: Int
)

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
                IntentoPruebaTable
                    .selectAll()
                    .andWhere { IntentoPruebaTable.usuarioIdCol eq usuarioId }
                    .toList() // traemos todos
                    .sortedByDescending { row: ResultRow -> row[IntentoPruebaTable.creadoEn] }
                    .take(20)
                    .map { row ->
                        IntentoPracticaRes(
                            intentoId = row[IntentoPruebaTable.intentoId].toString(),
                            pruebaId = row[IntentoPruebaTable.pruebaId].toString(),
                            fechaInicio = row[IntentoPruebaTable.fechaInicio],
                            fechaFin = row[IntentoPruebaTable.fechaFin],
                            puntaje = row[IntentoPruebaTable.puntaje]?.toInt(),
                            puntajeTotal = row[IntentoPruebaTable.puntajeTotal]
                        )
                    }
            }

            call.respond(intentos)
        }
    }
}
