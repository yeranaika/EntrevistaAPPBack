package data.repository.sesiones

import data.tables.sesiones.SesionPreguntaTable
import tables.cuestionario.preguntas.PreguntaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import plugins.DatabaseFactory
import java.util.UUID

/**
 * Modelo de datos para asociación sesión-pregunta.
 */
data class SesionPregunta(
    val sesionPreguntaId: UUID,
    val sesionId: UUID,
    val preguntaId: UUID?,
    val orden: Int,
    val textoRef: String?,
    val recomendaciones: String?,
    val tiempoEntregaMs: Int?
)

/**
 * Modelo de datos para pregunta (simplificado).
 */
data class Pregunta(
    val id: UUID,
    val tipoBanco: String,
    val nivel: String,
    val sector: String?,
    val texto: String,
    val pistas: String?,
    val activa: Boolean
)

/**
 * Repositorio para gestión de preguntas dentro de sesiones.
 */
class SesionPreguntaRepository(
    private val db: Database = DatabaseFactory.db
) {
    /**
     * Crea un registro de sesión_pregunta.
     */
    suspend fun create(
        sessionId: UUID,
        preguntaId: UUID,
        orden: Int
    ): SesionPregunta = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()

        SesionPreguntaTable.insert { st ->
            st[SesionPreguntaTable.sesionPreguntaId] = newId
            st[SesionPreguntaTable.sesionId] = sessionId
            st[SesionPreguntaTable.preguntaId] = preguntaId
            st[SesionPreguntaTable.orden] = orden
            st[SesionPreguntaTable.textoRef] = null
            st[SesionPreguntaTable.recomendaciones] = null
            st[SesionPreguntaTable.tiempoEntregaMs] = null
        }

        SesionPregunta(
            sesionPreguntaId = newId,
            sesionId = sessionId,
            preguntaId = preguntaId,
            orden = orden,
            textoRef = null,
            recomendaciones = null,
            tiempoEntregaMs = null
        )
    }

    /**
     * Busca una sesión_pregunta por su ID.
     */
    suspend fun findById(sessionPreguntaId: UUID): SesionPregunta? =
        newSuspendedTransaction(db = db) {
            SesionPreguntaTable
                .selectAll()
                .where { SesionPreguntaTable.sesionPreguntaId eq sessionPreguntaId }
                .limit(1)
                .singleOrNull()
                ?.toSesionPregunta()
        }

    /**
     * Obtiene los IDs de preguntas ya usadas en una sesión.
     */
    suspend fun getPreguntasUsadas(sessionId: UUID): List<UUID> =
        newSuspendedTransaction(db = db) {
            SesionPreguntaTable
                .select(SesionPreguntaTable.preguntaId)
                .where {
                    (SesionPreguntaTable.sesionId eq sessionId) and
                    (SesionPreguntaTable.preguntaId.isNotNull())
                }
                .mapNotNull { it[SesionPreguntaTable.preguntaId] }
        }

    /**
     * Busca la siguiente pregunta disponible que no haya sido usada.
     *
     * @param sessionId ID de la sesión actual
     * @param modo Modo de la sesión (tec, soft, mix)
     * @param nivel Nivel de dificultad (jr, mid, sr)
     * @param preguntasUsadas Lista de IDs de preguntas ya mostradas
     * @return Pregunta disponible o null si no hay más
     */
    suspend fun getNextPregunta(
        sessionId: UUID,
        modo: String,
        nivel: String,
        preguntasUsadas: List<UUID>
    ): Pregunta? = newSuspendedTransaction(db = db) {
        // Construir query base: preguntas activas del nivel correcto
        val query = PreguntaTable
            .selectAll()
            .where {
                (PreguntaTable.activa eq true) and
                (PreguntaTable.nivel eq nivel)
            }

        // Filtrar por modo (tipo de banco)
        when (modo) {
            "tec" -> query.andWhere { PreguntaTable.tipoBanco eq "tec" }
            "soft" -> query.andWhere { PreguntaTable.tipoBanco eq "soft" }
            "mix" -> {
                // Para modo mix, aceptar tanto tec como soft
                query.andWhere {
                    (PreguntaTable.tipoBanco eq "tec") or
                    (PreguntaTable.tipoBanco eq "soft")
                }
            }
        }

        // Excluir preguntas ya usadas
        if (preguntasUsadas.isNotEmpty()) {
            query.andWhere { PreguntaTable.id notInList preguntasUsadas }
        }

        // Seleccionar una pregunta aleatoria (usando RANDOM())
        // Nota: En PostgreSQL se usa RANDOM(), en otras bases puede variar
        val result = query
            .orderBy(org.jetbrains.exposed.sql.Random())
            .limit(1)
            .singleOrNull()
            ?.toPregunta()

        result
    }

    /**
     * Extension function para mapear ResultRow a SesionPregunta.
     */
    private fun ResultRow.toSesionPregunta() = SesionPregunta(
        sesionPreguntaId = this[SesionPreguntaTable.sesionPreguntaId],
        sesionId = this[SesionPreguntaTable.sesionId],
        preguntaId = this[SesionPreguntaTable.preguntaId],
        orden = this[SesionPreguntaTable.orden],
        textoRef = this[SesionPreguntaTable.textoRef],
        recomendaciones = this[SesionPreguntaTable.recomendaciones],
        tiempoEntregaMs = this[SesionPreguntaTable.tiempoEntregaMs]
    )

    /**
     * Extension function para mapear ResultRow a Pregunta.
     */
    private fun ResultRow.toPregunta() = Pregunta(
        id = this[PreguntaTable.id],
        tipoBanco = this[PreguntaTable.tipoBanco],
        nivel = this[PreguntaTable.nivel],
        sector = this[PreguntaTable.sector],
        texto = this[PreguntaTable.texto],
        pistas = this[PreguntaTable.pistas],
        activa = this[PreguntaTable.activa]
    )
}
