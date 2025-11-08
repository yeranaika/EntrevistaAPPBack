package data.repository.admin

import data.mapper.toPreguntaRes
import data.models.CreatePreguntaReq
import data.models.Nivel
import data.models.PreguntaRes
import data.models.TipoBanco
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import tables.cuestionario.preguntas.PreguntaTable

// --- helper portable para LOWER(expr) ---
private fun lower(expr: Expression<String>): Expression<String> =
    CustomFunction("lower", VarCharColumnType(), expr)

class PreguntaRepository(
    private val db: Database,
    private val json: Json
) {

    suspend fun create(req: CreatePreguntaReq): PreguntaRes = newSuspendedTransaction(db = db) {
        val insertedId = PreguntaTable.insertAndGetId {
            it[tipoBanco]  = req.tipoBanco.name
            it[nivel]      = req.nivel.name
            it[sector]     = req.sector
            it[texto]      = req.texto
            it[pistas]     = req.pistas?.let(json::encodeToString)     // guardamos JSON como TEXT
            it[historica]  = req.historica?.let(json::encodeToString)  // idem
            it[activa]     = req.activa
            // fecha_creacion la setea Postgres (DEFAULT now())
        }

        // Evita overload deprecado: selectAll().where { ... }.limit(1)
        val row = PreguntaTable
            .selectAll()
            .where { PreguntaTable.id eq insertedId }
            .limit(1)
            .single()

        row.toPreguntaRes(json)
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

            // Filtros
            params.activa?.let { base.andWhere { PreguntaTable.activa eq it } }
            params.nivel?.let { base.andWhere { PreguntaTable.nivel eq it.name } }
            params.tipoBanco?.let { base.andWhere { PreguntaTable.tipoBanco eq it.name } }

            // Búsqueda case-insensitive portable (LOWER(texto) LIKE lower(:term))
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
