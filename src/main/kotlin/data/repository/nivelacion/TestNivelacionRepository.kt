package data.repository.nivelacion

import tables.cuestionario.prueba.PruebaTable
import com.example.data.tables.IntentoPruebaTable
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
        // 1) Crear una prueba de nivelación
        val pruebaId = UUID.randomUUID()
        PruebaTable.insert { row ->
            row[id] = pruebaId
            row[tipoPrueba] = "nivel"
            row[PruebaTable.area] = area
            row[PruebaTable.nivel] = nivel
            row[metadata] = metaCargo
            row[activo] = true
        }

        // 2) Crear intento para ese usuario
        val now = OffsetDateTime.now().format(formatter)
        val intentoId = UUID.randomUUID()

        IntentoPruebaTable.insert { row ->
            row[IntentoPruebaTable.id] = intentoId
            row[IntentoPruebaTable.pruebaId] = pruebaId
            row[IntentoPruebaTable.usuarioId] = usuarioId
            row[IntentoPruebaTable.fechaInicio] = now
            row[IntentoPruebaTable.fechaFin] = now
            row[IntentoPruebaTable.puntaje] = puntaje.toBigDecimal()
            row[IntentoPruebaTable.recomendaciones] = feedback
            row[IntentoPruebaTable.puntajeTotal] = totalPreguntas   // usamos este campo para guardar total de preguntas
            row[IntentoPruebaTable.estado] = "completado"
            row[IntentoPruebaTable.tiempoTotalSegundos] = null
            row[IntentoPruebaTable.creadoEn] = now
            row[IntentoPruebaTable.actualizadoEn] = now
        }

        intentoId
    }

    /**
     * Versión simplificada para sistema de onboarding
     * Acepta habilidad y nivelSugerido numérico (1, 2, 3)
     */
    fun create(
        usuarioId: UUID,
        habilidad: String,
        nivelSugerido: Int,
        puntaje: Int,
        totalPreguntas: Int,
        preguntasCorrectas: Int,
        feedback: String
    ): UUID {
        // Convertir nivel numérico a texto
        val nivelTexto = when (nivelSugerido) {
            1 -> "jr"
            2 -> "mid"
            3 -> "sr"
            else -> "jr"
        }

        // Extraer área corta del cargo (primeras 5 letras o mapeo)
        val areaCorta = when {
            habilidad.contains("Desarrollador", ignoreCase = true) -> "tec"
            habilidad.contains("Analista", ignoreCase = true) -> "tec"
            habilidad.contains("DevOps", ignoreCase = true) -> "tec"
            habilidad.contains("Project", ignoreCase = true) -> "soft"
            habilidad.contains("Manager", ignoreCase = true) -> "soft"
            else -> "mix"
        }

        return create(
            usuarioId = usuarioId,
            area = areaCorta,
            nivel = nivelTexto,
            metaCargo = habilidad,
            puntaje = puntaje,
            totalPreguntas = totalPreguntas,
            preguntasCorrectas = preguntasCorrectas,
            feedback = feedback
        )
    }

    /**
     * Busca el test de nivelación más reciente de un usuario para una habilidad específica
     * Ahora busca por metadata (meta_cargo) en lugar de area
     */
    fun findLatestByUsuarioAndHabilidad(
        usuarioId: UUID,
        habilidad: String
    ): TestNivelacionRow? = transaction {
        IntentoPruebaTable
            .innerJoin(PruebaTable, { IntentoPruebaTable.pruebaId }, { PruebaTable.id })
            .selectAll()
            .where {
                (IntentoPruebaTable.usuarioId eq usuarioId) and
                (PruebaTable.tipoPrueba eq "nivel") and
                (PruebaTable.metadata eq habilidad)
            }
            .orderBy(IntentoPruebaTable.creadoEn, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { toRow(it) }
    }

    /**
     * Historial de tests de nivelación de un usuario.
     */
    fun findByUsuario(usuarioId: UUID): List<TestNivelacionRow> = transaction {
        IntentoPruebaTable
            .innerJoin(PruebaTable, { IntentoPruebaTable.pruebaId }, { PruebaTable.id })
            .selectAll()
            .where {
                (IntentoPruebaTable.usuarioId eq usuarioId) and
                (PruebaTable.tipoPrueba eq "nivel")
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
        IntentoPruebaTable
            .innerJoin(PruebaTable, { IntentoPruebaTable.pruebaId }, { PruebaTable.id })
            .selectAll()
            .where {
                (IntentoPruebaTable.usuarioId eq usuarioId) and
                (PruebaTable.tipoPrueba eq "nivel") and
                (PruebaTable.area eq area) and
                (PruebaTable.nivel eq nivel)
            }
            .orderBy(IntentoPruebaTable.creadoEn, SortOrder.DESC)
            .map { toRow(it) }
    }

    fun findById(intentoId: UUID): TestNivelacionRow? = transaction {
        IntentoPruebaTable
            .innerJoin(PruebaTable, { IntentoPruebaTable.pruebaId }, { PruebaTable.id })
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
            id = row[IntentoPruebaTable.id],
            usuarioId = row[IntentoPruebaTable.usuarioId],
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
