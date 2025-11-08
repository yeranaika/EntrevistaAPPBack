// data/repository/admin/repository-add-pregunta.kt
package data.repository.admin

import data.mapper.toPreguntaRes
import data.models.CreatePreguntaReq
import data.models.Nivel
import data.models.PreguntaRes
import data.models.TipoBanco
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq   // <-- IMPORT NECESARIO
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import tables.cuestionario.preguntas.PreguntaTable
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
            st[PreguntaTable.id]        = newId
            st[PreguntaTable.tipoBanco] = req.tipoBanco.name
            st[PreguntaTable.nivel]     = req.nivel.name
            st[PreguntaTable.sector]    = req.sector
            st[PreguntaTable.texto]     = req.texto
            st[PreguntaTable.pistas]    = req.pistas?.let(json::encodeToString)
            st[PreguntaTable.historica] = req.historica?.let(json::encodeToString)
            st[PreguntaTable.activa]    = req.activa
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
}
