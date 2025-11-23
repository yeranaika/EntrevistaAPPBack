package data.repository.nivelacion

import data.tables.cuestionario.prueba.PruebaTable
import data.tables.IntentoPruebaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.roundToInt

class TestNivelacionRepository {

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * Crea una PRUEBA de tipo 'nivelacion' y un INTENTO asociado para el usuario,
     * guardando puntaje, total de preguntas y feedback.
     *
     * Retorna el ID del intento (lo puedes usar como testId en las respuestas).
     */
    fun create(
        usuarioId: UUID,
        area: String,
        nivel: String,
        metaCargo: String?,
        puntaje: Int,            // 0–100
        totalPreguntas: Int,
        preguntasCorrectas: Int, // hoy se usa sólo para mostrar, el cálculo es simple
        feedback: String
    ): UUID = transaction {
        // 1) Crear una prueba de nivelación (si quisieras podrías reutilizar una por área/nivel)
        val pruebaId = PruebaTable.insertAndGetId { row ->
            row[tipoPrueba] = "nivelacion"
            row[PruebaTable.area] = area
            row[PruebaTable.nivel] = nivel
            row[metadata] = metaCargo
            row[activo] = true
        }.value

        // 2) Crear intento para ese usuario
        val now = OffsetDateTime.now().format(formatter)

        val intentoId = IntentoPruebaTable.insertAndGetId { row ->
            row[this.pruebaId] = pruebaId
            row[this.usuarioId] = usuarioId
            row[fechaInicio] = now
            row[fechaFin] = now
            row[this.puntaje] = puntaje.toBigDecimal()
            row[recomendaciones] = feedback
            row[puntajeTotal] = totalPreguntas   // usamos este campo para guardar total de preguntas
            row[estado] = "completado"
            row[tiempoTotalSegundos] = null
            row[creadoEn] = now
            row[actualizadoEn] = now
        }

        intentoId.value
    }

    /**
     * Historial de tests de nivelación de un usuario.
     */
    fun findByUsuario(usuarioId: UUID): List<TestNivelacionRow> = transaction {
        (IntentoPruebaTable innerJoin PruebaTable)
            .selectAll()
            .where {
                (IntentoPruebaTable.usuarioId eq usuarioId) and
                (PruebaTable.tipoPrueba eq "nivelacion")
            }
            .orderBy(IntentoPruebaTable.creadoEn, SortOrder.DESC)
            .map { toRow(it) }
    }

    /**
     * Historial filtrado por área+nivel (por si lo necesitas).
     */
    fun findByUsuarioAndAreaNivel(
        usuarioId: UUID,
        area: String,
        nivel: String
    ): List<TestNivelacionRow> = transaction {
        (IntentoPruebaTable innerJoin PruebaTable)
            .selectAll()
            .where {
                (IntentoPruebaTable.usuarioId eq usuarioId) and
                (PruebaTable.tipoPrueba eq "nivelacion") and
                (PruebaTable.area eq area) and
                (PruebaTable.nivel eq nivel)
            }
            .orderBy(IntentoPruebaTable.creadoEn, SortOrder.DESC)
            .map { toRow(it) }
    }

    fun findById(intentoId: UUID): TestNivelacionRow? = transaction {
        (IntentoPruebaTable innerJoin PruebaTable)
            .selectAll()
            .where { IntentoPruebaTable.id eq intentoId }
            .singleOrNull()
            ?.let { toRow(it) }
    }

    fun delete(intentoId: UUID): Int = transaction {
        IntentoPruebaTable.deleteWhere { IntentoPruebaTable.id eq intentoId }
    }

    private fun toRow(row: ResultRow): TestNivelacionRow {
        val total = row[IntentoPruebaTable.puntajeTotal]
        val puntajeDecimal = row[IntentoPruebaTable.puntaje]?.toDouble() ?: 0.0
        val puntajeInt = puntajeDecimal.roundToInt()

        // Aproximamos correctas = total * porcentaje / 100
        val correctas = if (total > 0) {
            (total * puntajeDecimal / 100.0).roundToInt()
        } else 0

        val fecha = row[IntentoPruebaTable.fechaFin] ?: row[IntentoPruebaTable.actualizadoEn]

        return TestNivelacionRow(
            id = row[IntentoPruebaTable.id].value,
            usuarioId = row[IntentoPruebaTable.usuarioId].value,
            area = row[PruebaTable.area],
            nivel = row[PruebaTable.nivel],
            metaCargo = row[PruebaTable.metadata],
            puntaje = puntajeInt,
            totalPreguntas = total,
            preguntasCorrectas = correctas,
            feedback = row[IntentoPruebaTable.recomendaciones],
            fechaCompletado = fecha
        )
    }
}

data class TestNivelacionRow(
    val id: UUID,
    val usuarioId: UUID,
    val area: String?,
    val nivel: String?,
    val metaCargo: String?,
    val puntaje: Int,
    val totalPreguntas: Int,
    val preguntasCorrectas: Int,
    val feedback: String?,
    val fechaCompletado: String?
)
