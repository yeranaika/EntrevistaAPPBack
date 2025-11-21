package data.repository.sesiones

import data.tables.sesiones.RetroalimentacionTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import plugins.DatabaseFactory
import java.util.UUID

/**
 * Modelo de datos para retroalimentación.
 */
data class Retroalimentacion(
    val retroalimentacionId: UUID,
    val respuestaId: UUID,
    val nivelFeedback: String,
    val enunciado: String?,
    val aciertos: List<String>,
    val faltantes: List<String>
)

/**
 * Repositorio para gestión de retroalimentación (feedback).
 */
class RetroalimentacionRepository(
    private val db: Database = DatabaseFactory.db,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Crea un registro de retroalimentación.
     *
     * @param respuestaId ID de la respuesta evaluada
     * @param nivelFeedback Nivel del feedback (free, premium, etc.)
     * @param enunciado Mensaje general del feedback
     * @param aciertos Lista de puntos positivos (se serializa a JSON)
     * @param faltantes Lista de áreas de mejora (se serializa a JSON)
     */
    suspend fun create(
        respuestaId: UUID,
        nivelFeedback: String,
        enunciado: String,
        aciertos: List<String>,
        faltantes: List<String>
    ): Retroalimentacion = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()

        RetroalimentacionTable.insert { st ->
            st[RetroalimentacionTable.retroalimentacionId] = newId
            st[RetroalimentacionTable.respuestaId] = respuestaId
            st[RetroalimentacionTable.nivelFeedback] = nivelFeedback
            st[RetroalimentacionTable.enunciado] = enunciado
            st[RetroalimentacionTable.aciertos] = json.encodeToString(aciertos)
            st[RetroalimentacionTable.faltantes] = json.encodeToString(faltantes)
        }

        Retroalimentacion(
            retroalimentacionId = newId,
            respuestaId = respuestaId,
            nivelFeedback = nivelFeedback,
            enunciado = enunciado,
            aciertos = aciertos,
            faltantes = faltantes
        )
    }

    /**
     * Busca retroalimentación por ID de respuesta.
     */
    suspend fun findByRespuestaId(respuestaId: UUID): Retroalimentacion? =
        newSuspendedTransaction(db = db) {
            RetroalimentacionTable
                .selectAll()
                .where { RetroalimentacionTable.respuestaId eq respuestaId }
                .limit(1)
                .singleOrNull()
                ?.toRetroalimentacion()
        }

    /**
     * Extension function para mapear ResultRow a Retroalimentacion.
     */
    private fun ResultRow.toRetroalimentacion(): Retroalimentacion {
        val aciertosJson = this[RetroalimentacionTable.aciertos]
        val faltantesJson = this[RetroalimentacionTable.faltantes]

        return Retroalimentacion(
            retroalimentacionId = this[RetroalimentacionTable.retroalimentacionId],
            respuestaId = this[RetroalimentacionTable.respuestaId],
            nivelFeedback = this[RetroalimentacionTable.nivelFeedback],
            enunciado = this[RetroalimentacionTable.enunciado],
            aciertos = aciertosJson?.let { json.decodeFromString(it) } ?: emptyList(),
            faltantes = faltantesJson?.let { json.decodeFromString(it) } ?: emptyList()
        )
    }
}
