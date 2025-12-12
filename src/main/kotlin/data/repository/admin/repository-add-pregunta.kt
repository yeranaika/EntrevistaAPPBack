// data/repository/admin/repository-add-pregunta.kt
package data.repository.admin

import data.mapper.toPreguntaRes
import data.models.ActualizarPreguntaReq
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
import data.tables.cuestionario.preguntas.PreguntaTable
import java.time.Instant
import java.util.UUID

private fun lower(expr: Expression<String>): Expression<String> =
    CustomFunction("lower", VarCharColumnType(), expr)

class PreguntaRepository(
    private val db: Database,
    private val json: Json
) {

    // ============================
    // CREATE
    // ============================
    suspend fun create(req: CreatePreguntaReq): PreguntaRes =
        newSuspendedTransaction(db = db) {
            val newId = UUID.randomUUID()
            val sector = req.sector?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("sector requerido")

            PreguntaTable.insert { st ->
                st[PreguntaTable.id]        = newId
                st[PreguntaTable.tipoBanco] = req.tipoBanco.name
                st[PreguntaTable.nivel]     = req.nivel.name
                st[PreguntaTable.sector]    = sector
                st[PreguntaTable.texto]     = req.texto
                st[PreguntaTable.tipoPregunta] = "opcion_multiple"  // o lo que uses por defecto

                // 🔹 SOLO seteamos la columna si viene algo
                req.pistas?.let { pistasMap ->
                    val jsonStr: String =
                        json.encodeToString<Map<String, String>>(pistasMap)
                    st[PreguntaTable.pistas] = jsonStr   // <- String, no String?
                }

                // por ahora no seteamos configRespuesta/configEvaluacion aquí
                // se pueden rellenar luego desde otro flujo de admin

                st[PreguntaTable.activa]        = req.activa
                st[PreguntaTable.fechaCreacion] = Instant.now()
            }

            PreguntaTable
                .selectAll()
                .where { PreguntaTable.id eq newId }
                .limit(1)
                .single()
                .toPreguntaRes(json)
        }

    // ============================
    // LIST
    // ============================
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

    // ============================
    // UPDATE
    // ============================
    suspend fun update(
        id: UUID,
        req: ActualizarPreguntaReq,
        adminId: String? = null
    ): PreguntaRes? =
        newSuspendedTransaction(db = db) {
            val preguntaActual = PreguntaTable
                .selectAll()
                .where { PreguntaTable.id eq id }
                .limit(1)
                .singleOrNull()
                ?: return@newSuspendedTransaction null

            PreguntaTable.update({ PreguntaTable.id eq id }) { st ->
                req.texto?.let { st[PreguntaTable.texto] = it }
                req.sector?.let { st[PreguntaTable.sector] = it }
                req.activa?.let { st[PreguntaTable.activa] = it }

                req.pistas?.let { pistasMap ->
                    val jsonStr: String =
                        json.encodeToString<Map<String, String>>(pistasMap)
                    st[PreguntaTable.pistas] = jsonStr     // <- String, no String?
                }
            }

            PreguntaTable
                .selectAll()
                .where { PreguntaTable.id eq id }
                .limit(1)
                .single()
                .toPreguntaRes(json)
        }

    // ============================
    // OTROS
    // ============================
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
