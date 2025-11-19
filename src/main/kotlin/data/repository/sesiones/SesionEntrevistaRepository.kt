package data.repository.sesiones

import data.tables.sesiones.SesionEntrevistaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import plugins.DatabaseFactory
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Modelo de datos para sesión de entrevista.
 */
data class SesionEntrevista(
    val sesionId: UUID,
    val usuarioId: UUID,
    val modo: String,
    val nivel: String,
    val fechaInicio: OffsetDateTime,
    val fechaFin: OffsetDateTime?,
    val esPremium: Boolean,
    val puntajeGeneral: BigDecimal?
)

/**
 * Repositorio para gestión de sesiones de entrevista.
 */
class SesionEntrevistaRepository(
    private val db: Database = DatabaseFactory.db
) {
    /**
     * Crea una nueva sesión de entrevista.
     */
    suspend fun create(
        usuarioId: UUID,
        modo: String,
        nivel: String,
        esPremium: Boolean = false
    ): SesionEntrevista = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        SesionEntrevistaTable.insert { st ->
            st[SesionEntrevistaTable.sesionId] = newId
            st[SesionEntrevistaTable.usuarioId] = usuarioId
            st[SesionEntrevistaTable.modo] = modo
            st[SesionEntrevistaTable.nivel] = nivel
            st[SesionEntrevistaTable.fechaInicio] = now
            st[SesionEntrevistaTable.fechaFin] = null
            st[SesionEntrevistaTable.esPremium] = esPremium
            st[SesionEntrevistaTable.puntajeGeneral] = null
        }

        SesionEntrevista(
            sesionId = newId,
            usuarioId = usuarioId,
            modo = modo,
            nivel = nivel,
            fechaInicio = now,
            fechaFin = null,
            esPremium = esPremium,
            puntajeGeneral = null
        )
    }

    /**
     * Busca una sesión por su ID.
     */
    suspend fun findById(sessionId: UUID): SesionEntrevista? =
        newSuspendedTransaction(db = db) {
            SesionEntrevistaTable
                .selectAll()
                .where { SesionEntrevistaTable.sesionId eq sessionId }
                .limit(1)
                .singleOrNull()
                ?.toSesionEntrevista()
        }

    /**
     * Finaliza una sesión, marcando fecha_fin y opcionalmente el puntaje.
     */
    suspend fun finalizar(sessionId: UUID, puntaje: Int?): Boolean =
        newSuspendedTransaction(db = db) {
            val updated = SesionEntrevistaTable.update(
                { SesionEntrevistaTable.sesionId eq sessionId }
            ) { st ->
                st[SesionEntrevistaTable.fechaFin] = OffsetDateTime.now()
                if (puntaje != null) {
                    st[SesionEntrevistaTable.puntajeGeneral] = BigDecimal(puntaje)
                }
            }

            updated > 0
        }

    /**
     * Obtiene las sesiones de un usuario.
     */
    suspend fun findByUsuarioId(
        usuarioId: UUID,
        limit: Int = 10
    ): List<SesionEntrevista> = newSuspendedTransaction(db = db) {
        SesionEntrevistaTable
            .selectAll()
            .where { SesionEntrevistaTable.usuarioId eq usuarioId }
            .orderBy(SesionEntrevistaTable.fechaInicio, SortOrder.DESC)
            .limit(limit)
            .map { it.toSesionEntrevista() }
    }

    /**
     * Extension function para mapear ResultRow a SesionEntrevista.
     */
    private fun ResultRow.toSesionEntrevista() = SesionEntrevista(
        sesionId = this[SesionEntrevistaTable.sesionId],
        usuarioId = this[SesionEntrevistaTable.usuarioId],
        modo = this[SesionEntrevistaTable.modo],
        nivel = this[SesionEntrevistaTable.nivel],
        fechaInicio = this[SesionEntrevistaTable.fechaInicio],
        fechaFin = this[SesionEntrevistaTable.fechaFin],
        esPremium = this[SesionEntrevistaTable.esPremium],
        puntajeGeneral = this[SesionEntrevistaTable.puntajeGeneral]
    )
}
