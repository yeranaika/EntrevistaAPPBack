package data.repository.sesiones

import data.tables.sesiones.RespuestaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import plugins.DatabaseFactory
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Modelo de datos para respuesta de usuario.
 */
data class Respuesta(
    val respuestaId: UUID,
    val sesionPreguntaId: UUID,
    val usuarioId: UUID,
    val texto: String,
    val fechaCreacion: OffsetDateTime,
    val tokensIn: Int?
)

/**
 * Repositorio para gestión de respuestas de usuarios en sesiones.
 */
class RespuestaRepository(
    private val db: Database = DatabaseFactory.db
) {
    /**
     * Crea una respuesta de usuario.
     */
    suspend fun create(
        sessionPreguntaId: UUID,
        usuarioId: UUID,
        texto: String,
        tokensIn: Int? = null
    ): Respuesta = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        RespuestaTable.insert { st ->
            st[RespuestaTable.respuestaId] = newId
            st[RespuestaTable.sesionPreguntaId] = sessionPreguntaId
            st[RespuestaTable.usuarioId] = usuarioId
            st[RespuestaTable.texto] = texto
            st[RespuestaTable.fechaCreacion] = now
            st[RespuestaTable.tokensIn] = tokensIn
        }

        Respuesta(
            respuestaId = newId,
            sesionPreguntaId = sessionPreguntaId,
            usuarioId = usuarioId,
            texto = texto,
            fechaCreacion = now,
            tokensIn = tokensIn
        )
    }

    /**
     * Busca una respuesta por su ID.
     */
    suspend fun findById(respuestaId: UUID): Respuesta? =
        newSuspendedTransaction(db = db) {
            RespuestaTable
                .selectAll()
                .where { RespuestaTable.respuestaId eq respuestaId }
                .limit(1)
                .singleOrNull()
                ?.toRespuesta()
        }

    /**
     * Busca la respuesta asociada a una sesión_pregunta específica.
     */
    suspend fun findBySesionPreguntaId(sesionPreguntaId: UUID): Respuesta? =
        newSuspendedTransaction(db = db) {
            RespuestaTable
                .selectAll()
                .where { RespuestaTable.sesionPreguntaId eq sesionPreguntaId }
                .limit(1)
                .singleOrNull()
                ?.toRespuesta()
        }

    /**
     * Extension function para mapear ResultRow a Respuesta.
     */
    private fun ResultRow.toRespuesta() = Respuesta(
        respuestaId = this[RespuestaTable.respuestaId],
        sesionPreguntaId = this[RespuestaTable.sesionPreguntaId],
        usuarioId = this[RespuestaTable.usuarioId],
        texto = this[RespuestaTable.texto],
        fechaCreacion = this[RespuestaTable.fechaCreacion],
        tokensIn = this[RespuestaTable.tokensIn]
    )
}
