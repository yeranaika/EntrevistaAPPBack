package data.repository.cuestionario

import com.example.data.models.OpcionRespuesta
import com.example.data.models.PreguntaConOrden
import com.example.data.tables.RespuestaPruebaTable
import data.models.cuestionario.AsociarPreguntaRequest
import data.models.cuestionario.PreguntaAsignadaResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import tables.cuestionario.prueba.PruebaPreguntaTable
import tables.cuestionario.preguntas.PreguntaTable
import java.util.UUID

private val opcionesSerializer = kotlinx.serialization.builtins.ListSerializer(OpcionRespuesta.serializer())

class PruebaCuestionarioRepository(
    private val db: Database,
    private val json: Json
) {

    suspend fun asociarPregunta(
        pruebaId: UUID,
        preguntaId: UUID,
        payload: AsociarPreguntaRequest
    ): PreguntaAsignadaResponse = newSuspendedTransaction(db = db) {
        require(payload.orden > 0) { "El orden debe ser mayor a 0" }

        val preguntaExiste = !PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq preguntaId }
            .limit(1)
            .empty()
        if (!preguntaExiste) {
            throw IllegalArgumentException("Pregunta no encontrada")
        }

        val ordenOcupado = !PruebaPreguntaTable
            .selectAll()
            .where { (PruebaPreguntaTable.pruebaId eq pruebaId) and (PruebaPreguntaTable.orden eq payload.orden) }
            .limit(1)
            .empty()
        if (ordenOcupado) {
            throw IllegalStateException("El orden ${payload.orden} ya esta en uso para esta prueba")
        }

        val newId = UUID.randomUUID()
        val opcionesJson = payload.opciones
            ?.takeIf { it.isNotEmpty() }
            ?.let { json.encodeToString(opcionesSerializer, it) }
        val clave = payload.claveCorrecta?.trim()

        PruebaPreguntaTable.insert {
            it[id] = newId
            it[this.pruebaId] = pruebaId
            it[this.preguntaId] = preguntaId
            it[orden] = payload.orden
            it[opciones] = opcionesJson
            it[claveCorrecta] = clave
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
                .where { (PruebaPreguntaTable.pruebaId eq pruebaId) and (PruebaPreguntaTable.orden greater ordenActual) }
                .orderBy(PruebaPreguntaTable.orden to SortOrder.ASC)
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
                ?.toPreguntaConOrden()
        }

    suspend fun obtenerAsignacion(pruebaId: UUID, preguntaId: UUID): PreguntaAsignadaResponse? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where { (PruebaPreguntaTable.pruebaId eq pruebaId) and (PruebaPreguntaTable.preguntaId eq preguntaId) }
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
                .where { (RespuestaPruebaTable.intentoId eq intentoId) and (RespuestaPruebaTable.preguntaId eq pruebaPreguntaId) }
                .limit(1)
                .empty()
        }

    private suspend fun obtenerPreguntaPorOrden(pruebaId: UUID, orden: Int): PreguntaAsignadaRow? =
        newSuspendedTransaction(db = db) {
            baseQuery()
                .selectAll()
                .where { (PruebaPreguntaTable.pruebaId eq pruebaId) and (PruebaPreguntaTable.orden eq orden) }
                .limit(1)
                .singleOrNull()
                ?.toAsignada(json)
        }

    private fun baseQuery(): ColumnSet = PruebaPreguntaTable.join(
        otherTable = PreguntaTable,
        joinType = JoinType.INNER,
        additionalConstraint = { PruebaPreguntaTable.preguntaId eq PreguntaTable.id }
    )
}

private data class PreguntaAsignadaRow(
    val asignacionId: UUID,
    val pruebaId: UUID,
    val preguntaId: UUID,
    val orden: Int,
    val texto: String,
    val tipoPregunta: String,
    val opciones: List<OpcionRespuesta>?,
    val claveCorrecta: String?
)

private fun ResultRow.toAsignada(json: Json): PreguntaAsignadaRow {
    val opcionesRaw = this[PruebaPreguntaTable.opciones]
    val opciones = opcionesRaw?.let { raw ->
        runCatching { json.decodeFromString(opcionesSerializer, raw) }
            .getOrNull()
    }
    return PreguntaAsignadaRow(
        asignacionId = this[PruebaPreguntaTable.id],
        pruebaId = this[PruebaPreguntaTable.pruebaId],
        preguntaId = this[PruebaPreguntaTable.preguntaId],
        orden = this[PruebaPreguntaTable.orden],
        texto = this[PreguntaTable.texto],
        tipoPregunta = this[PreguntaTable.tipoBanco],
        opciones = opciones,
        claveCorrecta = this[PruebaPreguntaTable.claveCorrecta]
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
        claveCorrecta = claveCorrecta
    )


