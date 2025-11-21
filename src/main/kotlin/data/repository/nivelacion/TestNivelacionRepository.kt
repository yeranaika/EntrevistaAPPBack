package data.repository.nivelacion

import data.tables.nivelacion.TestNivelacionTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class TestNivelacionRepository {

    /**
     * Guarda el resultado de un test de nivelación completado
     */
    fun create(
        usuarioId: UUID,
        habilidad: String,
        puntaje: Int,
        totalPreguntas: Int,
        preguntasCorrectas: Int,
        nivelSugerido: Int,
        feedback: String
    ): UUID = transaction {
        val id = TestNivelacionTable.insertAndGetId {
            it[this.usuarioId] = usuarioId
            it[this.habilidad] = habilidad
            it[this.puntaje] = puntaje
            it[this.totalPreguntas] = totalPreguntas
            it[this.preguntasCorrectas] = preguntasCorrectas
            it[this.nivelSugerido] = nivelSugerido
            it[this.feedback] = feedback
            it[this.fechaCompletado] = OffsetDateTime.now()
        }
        id.value
    }

    /**
     * Obtiene el historial de tests de un usuario
     */
    fun findByUsuario(usuarioId: UUID): List<TestNivelacionRow> = transaction {
        TestNivelacionTable
            .selectAll()
            .where { TestNivelacionTable.usuarioId eq usuarioId }
            .orderBy(TestNivelacionTable.fechaCompletado, SortOrder.DESC)
            .map { toRow(it) }
    }

    /**
     * Obtiene el historial de tests de un usuario para una habilidad específica
     */
    fun findByUsuarioAndHabilidad(usuarioId: UUID, habilidad: String): List<TestNivelacionRow> = transaction {
        TestNivelacionTable
            .selectAll()
            .where {
                (TestNivelacionTable.usuarioId eq usuarioId) and
                (TestNivelacionTable.habilidad eq habilidad)
            }
            .orderBy(TestNivelacionTable.fechaCompletado, SortOrder.DESC)
            .map { toRow(it) }
    }

    /**
     * Obtiene el último test de nivelación de un usuario para una habilidad
     */
    fun findLatestByUsuarioAndHabilidad(usuarioId: UUID, habilidad: String): TestNivelacionRow? = transaction {
        TestNivelacionTable
            .selectAll()
            .where {
                (TestNivelacionTable.usuarioId eq usuarioId) and
                (TestNivelacionTable.habilidad eq habilidad)
            }
            .orderBy(TestNivelacionTable.fechaCompletado, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { toRow(it) }
    }

    /**
     * Busca un test por ID
     */
    fun findById(testId: UUID): TestNivelacionRow? = transaction {
        TestNivelacionTable
            .selectAll()
            .where { TestNivelacionTable.id eq testId }
            .singleOrNull()
            ?.let { toRow(it) }
    }

    /**
     * Obtiene estadísticas de tests por habilidad para un usuario
     */
    fun getStatsForUsuario(usuarioId: UUID): List<HabilidadStats> = transaction {
        TestNivelacionTable
            .select(
                TestNivelacionTable.habilidad,
                TestNivelacionTable.puntaje.avg(),
                TestNivelacionTable.nivelSugerido.max(),
                TestNivelacionTable.id.count()
            )
            .where { TestNivelacionTable.usuarioId eq usuarioId }
            .groupBy(TestNivelacionTable.habilidad)
            .map { row ->
                HabilidadStats(
                    habilidad = row[TestNivelacionTable.habilidad],
                    puntajePromedio = row[TestNivelacionTable.puntaje.avg()]?.toInt() ?: 0,
                    nivelMaximo = row[TestNivelacionTable.nivelSugerido.max()] ?: 1,
                    cantidadTests = row[TestNivelacionTable.id.count()].toInt()
                )
            }
    }

    /**
     * Elimina un test del historial
     */
    fun delete(testId: UUID): Int = transaction {
        TestNivelacionTable.deleteWhere { TestNivelacionTable.id eq testId }
    }

    private fun toRow(row: ResultRow): TestNivelacionRow {
        return TestNivelacionRow(
            id = row[TestNivelacionTable.id].value,
            usuarioId = row[TestNivelacionTable.usuarioId],
            habilidad = row[TestNivelacionTable.habilidad],
            puntaje = row[TestNivelacionTable.puntaje],
            totalPreguntas = row[TestNivelacionTable.totalPreguntas],
            preguntasCorrectas = row[TestNivelacionTable.preguntasCorrectas],
            nivelSugerido = row[TestNivelacionTable.nivelSugerido],
            feedback = row[TestNivelacionTable.feedback],
            fechaCompletado = row[TestNivelacionTable.fechaCompletado]
        )
    }
}

data class TestNivelacionRow(
    val id: UUID,
    val usuarioId: UUID,
    val habilidad: String,
    val puntaje: Int,
    val totalPreguntas: Int,
    val preguntasCorrectas: Int,
    val nivelSugerido: Int,
    val feedback: String?,
    val fechaCompletado: OffsetDateTime
)

data class HabilidadStats(
    val habilidad: String,
    val puntajePromedio: Int,
    val nivelMaximo: Int,
    val cantidadTests: Int
)
