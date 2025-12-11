package data.repository.cuestionario

import com.example.data.models.OpcionRespuesta
import com.example.data.models.PreguntaConOrden
import data.models.cuestionario.AsociarPreguntaRequest
import data.models.cuestionario.PreguntaAsignadaResponse
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

import com.example.data.tables.RespuestaPruebaTable
import data.tables.cuestionario.prueba.PruebaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable
import data.tables.cuestionario.preguntas.PreguntaTable

private val opcionesSerializer = ListSerializer(OpcionRespuesta.serializer())

class PruebaCuestionarioRepository(
    private val db: Database,
    private val json: Json
) {

    suspend fun asociarPregunta(
        pruebaId: UUID,
        preguntaId: UUID,
        payload: AsociarPreguntaRequest,
        validarConsistencia: Boolean = true
    ): PreguntaAsignadaResponse = newSuspendedTransaction(db = db) {
        require(payload.orden > 0) { "El orden debe ser mayor a 0" }

        val preguntaRow = PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq preguntaId }
            .limit(1)
            .singleOrNull()
            ?: throw IllegalArgumentException("Pregunta no encontrada")

        val ordenOcupado = !PruebaPreguntaTable
            .selectAll()
            .where {
                (PruebaPreguntaTable.pruebaId eq pruebaId) and
                (PruebaPreguntaTable.orden eq payload.orden)
            }
            .limit(1)
            .empty()

        if (ordenOcupado) {
            throw IllegalStateException("El orden ${payload.orden} ya esta en uso para esta prueba")
        }

        if (validarConsistencia) {
            val pruebaRow = PruebaTable
                .selectAll()
                .where { PruebaTable.id eq pruebaId }
                .limit(1)
                .singleOrNull()
                ?: throw IllegalArgumentException("Prueba no encontrada")

            val nivelPrueba = pruebaRow[PruebaTable.nivel]
            val areaPrueba = pruebaRow[PruebaTable.area]

            val nivelPregunta = preguntaRow[PreguntaTable.nivel]
            val tipoPregunta = preguntaRow[PreguntaTable.tipoBanco]

            if (nivelPrueba != null && nivelPregunta != nivelPrueba) {
                throw IllegalArgumentException(
                    "Inconsistencia de nivel: La pregunta tiene nivel '$nivelPregunta' pero la prueba requiere nivel '$nivelPrueba'"
                )
            }

            if (areaPrueba != null && tipoPregunta != areaPrueba) {
                throw IllegalArgumentException(
                    "Inconsistencia de área: La pregunta es tipo '$tipoPregunta' pero la prueba es área '$areaPrueba'"
                )
            }
        }

        val newId = UUID.randomUUID()
        val opcionesJson = payload.opciones
            ?.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(opcionesSerializer, it) }
        val clave = payload.claveCorrecta?.trim()

        PruebaPreguntaTable.insert {
            it[PruebaPreguntaTable.id] = newId
            it[PruebaPreguntaTable.pruebaId] = pruebaId
            it[PruebaPreguntaTable.preguntaId] = preguntaId
            it[PruebaPreguntaTable.orden] = payload.orden
            it[PruebaPreguntaTable.opciones] = opcionesJson
            it[PruebaPreguntaTable.claveCorrecta] = clave
        }

        baseQuery()
            .selectAll()
            .where { PruebaPreguntaTable.id eq newId }
            .limit(1)
            .single()
            .toAsignada(json)
            .toResponse()
    }

    suspend fun obtenerPrimeraPregunta(pruebaId: UUID): PreguntaConOrden? =
        obtenerPreguntaPorOrden(pruebaId, 1)?.toPreguntaConOrden()

    suspend fun obtenerSiguientePregunta(pruebaId: UUID, ordenActual: Int): PreguntaConOrden? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where {
                    (PruebaPreguntaTable.pruebaId eq pruebaId) and
                    (PruebaPreguntaTable.orden greater ordenActual)
                }
                .orderBy(PruebaPreguntaTable.orden, SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
                ?.toPreguntaConOrden()
        }

    suspend fun obtenerAsignacion(pruebaId: UUID, preguntaId: UUID): PreguntaAsignadaResponse? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where {
                    (PruebaPreguntaTable.pruebaId eq pruebaId) and
                    (PruebaPreguntaTable.preguntaId eq preguntaId)
                }
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
                ?.toResponse()
        }

    suspend fun obtenerAsignacionPorId(pruebaPreguntaId: UUID): PreguntaAsignadaResponse? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where { PruebaPreguntaTable.id eq pruebaPreguntaId }
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
                ?.toResponse()
        }

    suspend fun totalPreguntas(pruebaId: UUID): Int = newSuspendedTransaction(db = db) {
        PruebaPreguntaTable
            .selectAll()
            .where { PruebaPreguntaTable.pruebaId eq pruebaId }
            .count()
            .toInt()
    }

    suspend fun respuestaYaRegistrada(intentoId: UUID, pruebaPreguntaId: UUID): Boolean =
        newSuspendedTransaction(db = db) {
            !RespuestaPruebaTable
                .selectAll()
                .where {
                    (RespuestaPruebaTable.intentoId eq intentoId) and
                    (RespuestaPruebaTable.preguntaId eq pruebaPreguntaId)
                }
                .limit(1)
                .empty()
        }

    // ----------------- Helpers privados -----------------

    private suspend fun obtenerPreguntaPorOrden(pruebaId: UUID, orden: Int): PreguntaAsignadaRow? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where {
                    (PruebaPreguntaTable.pruebaId eq pruebaId) and
                    (PruebaPreguntaTable.orden eq orden)
                }
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
        }

    private fun baseQuery(): ColumnSet =
        PruebaPreguntaTable.join(
            otherTable = PreguntaTable,
            joinType = JoinType.INNER,
            additionalConstraint = { PruebaPreguntaTable.preguntaId eq PreguntaTable.id }
        )
}

// ----------------- Mapeos a DTOs -----------------

private data class PreguntaAsignadaRow(
    val asignacionId: UUID,
    val pruebaId: UUID,
    val preguntaId: UUID,
    val orden: Int,
    val texto: String,
    val tipoPregunta: String,
    val opciones: List<OpcionRespuesta>?,
    val claveCorrecta: String?,
    val configEvaluacion: JsonElement?     // 👈 nuevo campo interno
)

private fun ResultRow.toAsignada(json: Json): PreguntaAsignadaRow {
    val opcionesRaw = this[PruebaPreguntaTable.opciones]
    val opciones = opcionesRaw?.let { raw ->
        runCatching { json.decodeFromString(opcionesSerializer, raw) }
            .getOrNull()
    }

    // Leemos el JSONB de config_evaluacion como String y lo parseamos a JsonElement
    val configEvalRaw: String? = this[PreguntaTable.configEvaluacion]
    val configEvalJson: JsonElement? = configEvalRaw?.let { raw ->
        runCatching { json.parseToJsonElement(raw) }.getOrNull()
    }

    return PreguntaAsignadaRow(
        asignacionId = this[PruebaPreguntaTable.id],
        pruebaId = this[PruebaPreguntaTable.pruebaId],
        preguntaId = this[PruebaPreguntaTable.preguntaId],
        orden = this[PruebaPreguntaTable.orden],
        texto = this[PreguntaTable.texto],
        tipoPregunta = this[PreguntaTable.tipoBanco],   // como lo tenías
        opciones = opciones,
        claveCorrecta = this[PruebaPreguntaTable.claveCorrecta],
        configEvaluacion = configEvalJson
    )
}

private fun PreguntaAsignadaRow.toPreguntaConOrden(): PreguntaConOrden =
    PreguntaConOrden(
        preguntaId = preguntaId.toString(),
        orden = orden,
        textoPregunta = texto,
        opciones = opciones,
        tipoPregunta = tipoPregunta
    )

private fun PreguntaAsignadaRow.toResponse(): PreguntaAsignadaResponse =
    PreguntaAsignadaResponse(
        pruebaPreguntaId = asignacionId.toString(),
        pruebaId = pruebaId.toString(),
        preguntaId = preguntaId.toString(),
        orden = orden,
        textoPregunta = texto,
        tipoPregunta = tipoPregunta,
        opciones = opciones,
        claveCorrecta = claveCorrecta,
        configEvaluacion = configEvaluacion
    )
