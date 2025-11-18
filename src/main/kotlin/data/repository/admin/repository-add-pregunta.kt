// data/repository/admin/repository-add-pregunta.kt
package data.repository.admin

import data.mapper.toPreguntaRes
import data.models.ActualizarPreguntaReq
import data.models.CreatePreguntaReq
import data.models.Nivel
import data.models.PreguntaRes
import data.models.TipoBanco
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq   // <-- IMPORT NECESARIO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import tables.cuestionario.preguntas.PreguntaTable
import java.time.OffsetDateTime
import java.util.UUID

private fun lower(expr: Expression<String>): Expression<String> =
    CustomFunction("lower", VarCharColumnType(), expr)

class PreguntaRepository(
    private val db: Database,
    private val json: Json
) {

    suspend fun create(req: CreatePreguntaReq): PreguntaRes = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()

        PreguntaTable.insert { st ->
            st[PreguntaTable.id]             = newId
            st[PreguntaTable.tipoBanco]      = req.tipoBanco.name
            st[PreguntaTable.nivel]          = req.nivel.name
            st[PreguntaTable.sector]         = req.sector
            st[PreguntaTable.texto]          = req.texto
            st[PreguntaTable.pistas]         = req.pistas?.let(json::encodeToString)
            st[PreguntaTable.historica]      = req.historica?.let(json::encodeToString)
            st[PreguntaTable.activa]         = req.activa
            st[PreguntaTable.fechaCreacion]  = OffsetDateTime.now()
        }

        // ✅ DSL nuevo, sin método deprecado
        PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq newId }
            .limit(1)
            .single()
            .toPreguntaRes(json)
    }

    data class ListParams(
        val activa: Boolean? = null,
        val nivel: Nivel? = null,
        val tipoBanco: TipoBanco? = null,
        val q: String? = null,
        val page: Int = 1,
        val size: Int = 20
    )

    suspend fun list(params: ListParams): Pair<List<PreguntaRes>, Long> =
        newSuspendedTransaction(db = db) {
            val base = PreguntaTable.selectAll()

            params.activa?.let { base.andWhere { PreguntaTable.activa eq it } }
            params.nivel?.let { base.andWhere { PreguntaTable.nivel eq it.name } }
            params.tipoBanco?.let { base.andWhere { PreguntaTable.tipoBanco eq it.name } }

            if (!params.q.isNullOrBlank()) {
                val term = "%${params.q.trim()}%"
                base.andWhere { lower(PreguntaTable.texto) like term.lowercase() }
            }

            val total = base.count()
            val items = base
                .orderBy(PreguntaTable.fechaCreacion, SortOrder.DESC)
                .limit(params.size, offset = ((params.page - 1) * params.size).toLong())
                .map { it.toPreguntaRes(json) }

            items to total
        }

    suspend fun update(id: UUID, req: ActualizarPreguntaReq, adminId: String? = null): PreguntaRes? = newSuspendedTransaction(db = db) {
        // Verificar que la pregunta existe
        val preguntaActual = PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq id }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction null

        // Preparar histórico automático si hay cambios
        val historicaActual = preguntaActual[PreguntaTable.historica]?.let {
            runCatching { json.decodeFromString<MutableMap<String, String>>(it) }.getOrNull() ?: mutableMapOf()
        } ?: mutableMapOf()

        // Registrar cambios en histórico
        val timestamp = OffsetDateTime.now().toString()
        val cambios = mutableListOf<String>()

        req.texto?.let { cambios.add("texto") }
        req.sector?.let { cambios.add("sector") }
        req.activa?.let {
            val cambio = if (it) "activada" else "desactivada"
            cambios.add(cambio)
        }
        req.pistas?.let { cambios.add("pistas") }

        if (cambios.isNotEmpty()) {
            val cambioStr = cambios.joinToString(", ")
            val quien = adminId?.let { " por admin:$it" } ?: ""
            historicaActual["edit_$timestamp"] = "Editado: $cambioStr$quien"
        }

        // Actualizar histórico: merge del actual + nuevo proporcionado
        val historicoFinal = if (req.historica != null) {
            // Si se proporciona histórico nuevo, hacer merge
            historicaActual.putAll(req.historica)
            historicaActual
        } else {
            // Si no se proporciona, solo usar el histórico automático
            historicaActual
        }

        // Crear serializador para Map<String, String>
        val mapSerializer = MapSerializer(String.serializer(), String.serializer())

        // Actualizar solo los campos proporcionados
        PreguntaTable.update({ PreguntaTable.id eq id }) { st ->
            req.texto?.let { st[PreguntaTable.texto] = it }
            req.sector?.let { st[PreguntaTable.sector] = it }
            req.activa?.let { st[PreguntaTable.activa] = it }
            req.pistas?.let { pistas ->
                st[PreguntaTable.pistas] = json.encodeToString(mapSerializer, pistas)
            }

            if (historicoFinal.isNotEmpty()) {
                st[PreguntaTable.historica] = json.encodeToString(mapSerializer, historicoFinal)
            }
        }

        // Retornar la pregunta actualizada
        PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq id }
            .limit(1)
            .single()
            .toPreguntaRes(json)
    }

    suspend fun totalPreguntas(activa: Boolean? = null): Long =
        newSuspendedTransaction(db = db) {
            val query = PreguntaTable.selectAll()
            activa?.let { query.andWhere { PreguntaTable.activa eq it } }
            query.count()
        }

    suspend fun ultimasPreguntas(limit: Int = 5): List<PreguntaRes> =
        newSuspendedTransaction(db = db) {
            require(limit in 1..50) { "limit invalido" }

            PreguntaTable
                .selectAll()
                .orderBy(PreguntaTable.fechaCreacion, SortOrder.DESC)
                .limit(limit)
                .map { it.toPreguntaRes(json) }
        }
}
