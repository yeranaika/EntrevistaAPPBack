package data.repository.admin

import com.example.data.models.OpcionRespuesta
import data.models.Nivel
import data.models.cuestionario.AreaPrueba
import data.models.cuestionario.CrearPruebaReq
import data.models.cuestionario.PreguntaAsignadaResponse
import data.models.cuestionario.PruebaCompletaRes
import data.models.cuestionario.PruebaRes
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.count
import java.util.UUID

import data.tables.cuestionario.preguntas.PreguntaTable
import data.tables.cuestionario.prueba.PruebaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable


class PruebaRepository(
    private val db: Database,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    data class ListParams(
        val activo: Boolean? = null,
        val nivel: Nivel? = null,
        val area: AreaPrueba? = null,
        val tipoPrueba: String? = null,
        val page: Int = 1,
        val size: Int = 20
    )

    suspend fun create(req: CrearPruebaReq): PruebaRes = newSuspendedTransaction(db = db) {
        val newId = UUID.randomUUID()

        PruebaTable.insert { st ->
            st[PruebaTable.id] = newId
            st[PruebaTable.tipoPrueba] = req.tipoPrueba
            st[PruebaTable.area] = req.area?.name
            st[PruebaTable.nivel] = req.nivel?.name
            st[PruebaTable.metadata] = req.metadata?.let { json.encodeToString(it) }
            st[PruebaTable.activo] = true
        }

        PruebaTable
            .selectAll()
            .where { PruebaTable.id eq newId }
            .limit(1)
            .single()
            .toPruebaRes(json)
    }

    suspend fun getPruebaConPreguntas(pruebaId: UUID): PruebaCompletaRes? =
        newSuspendedTransaction(db = db) {
            val pruebaRow = PruebaTable
                .selectAll()
                .where { PruebaTable.id eq pruebaId }
                .limit(1)
                .singleOrNull()
                ?: return@newSuspendedTransaction null

            val preguntas = PruebaPreguntaTable
                .join(
                    otherTable = PreguntaTable,
                    joinType = JoinType.INNER,
                    additionalConstraint = { PruebaPreguntaTable.preguntaId eq PreguntaTable.id }
                )
                .selectAll()
                .where { PruebaPreguntaTable.pruebaId eq pruebaId }
                .orderBy(PruebaPreguntaTable.orden to SortOrder.ASC)
                .map { row ->
                    val opcionesJson = row[PruebaPreguntaTable.opciones]
                    val opciones = opcionesJson?.let {
                        Json.decodeFromString<List<OpcionRespuesta>>(it)
                    }

                    PreguntaAsignadaResponse(
                        pruebaPreguntaId = row[PruebaPreguntaTable.id].toString(),
                        pruebaId = row[PruebaPreguntaTable.pruebaId].toString(),
                        preguntaId = row[PruebaPreguntaTable.preguntaId].toString(),
                        orden = row[PruebaPreguntaTable.orden],
                        textoPregunta = row[PreguntaTable.texto],
                        tipoPregunta = row[PreguntaTable.tipoBanco],
                        opciones = opciones,
                        claveCorrecta = row[PruebaPreguntaTable.claveCorrecta]
                    )
                }

            pruebaRow.toPruebaCompletaRes(json, preguntas)
        }

    suspend fun update(
        id: UUID,
        req: data.models.cuestionario.ActualizarPruebaReq,
        adminId: String? = null
    ): PruebaRes? = newSuspendedTransaction(db = db) {
        val pruebaActual = PruebaTable
            .selectAll()
            .where { PruebaTable.id eq id }
            .limit(1)
            .singleOrNull()
            ?: return@newSuspendedTransaction null

        val historicaActual = pruebaActual[PruebaTable.historica]?.let {
            runCatching { json.decodeFromString<MutableMap<String, String>>(it) }
                .getOrNull() ?: mutableMapOf()
        } ?: mutableMapOf()

        val timestamp = java.time.OffsetDateTime.now().toString()
        val cambios = mutableListOf<String>()

        req.tipoPrueba?.let { cambios.add("tipoPrueba") }
        req.area?.let { cambios.add("area") }
        req.nivel?.let { cambios.add("nivel") }
        req.metadata?.let { cambios.add("metadata") }
        req.activo?.let { cambios.add(if (it) "activada" else "desactivada") }

        if (cambios.isNotEmpty()) {
            val cambioStr = cambios.joinToString(", ")
            val quien = adminId?.let { " por admin:$it" } ?: ""
            historicaActual["edit_$timestamp"] = "Editado: $cambioStr$quien"
        }

        PruebaTable.update({ PruebaTable.id eq id }) { st ->
            req.tipoPrueba?.let { st[PruebaTable.tipoPrueba] = it }
            req.area?.let { st[PruebaTable.area] = it.name }
            req.nivel?.let { st[PruebaTable.nivel] = it.name }
            req.metadata?.let { st[PruebaTable.metadata] = json.encodeToString(it) }
            req.activo?.let { st[PruebaTable.activo] = it }

            if (historicaActual.isNotEmpty()) {
                st[PruebaTable.historica] = json.encodeToString(historicaActual)
            }
        }

        PruebaTable
            .selectAll()
            .where { PruebaTable.id eq id }
            .limit(1)
            .single()
            .toPruebaRes(json)
    }

    suspend fun list(params: ListParams): Pair<List<PruebaRes>, Long> =
        newSuspendedTransaction(db = db) {
            var query = PruebaTable.selectAll()

            params.activo?.let { activo ->
                query = query.andWhere { PruebaTable.activo eq activo }
            }
            params.nivel?.let { nivel ->
                query = query.andWhere { PruebaTable.nivel eq nivel.name }
            }
            params.area?.let { area ->
                query = query.andWhere { PruebaTable.area eq area.name }
            }
            params.tipoPrueba?.let { tipo ->
                query = query.andWhere { PruebaTable.tipoPrueba eq tipo }
            }

            val total = query.count()
            val items = query
                .limit(params.size, offset = ((params.page - 1) * params.size).toLong())
                .map { it.toPruebaRes(json) }

            items to total
        }
}

private fun ResultRow.toPruebaRes(json: Json) = PruebaRes(
    pruebaId = this[PruebaTable.id].toString(),
    tipoPrueba = this[PruebaTable.tipoPrueba],
    area = this[PruebaTable.area]?.let { runCatching { AreaPrueba.valueOf(it) }.getOrNull() },
    nivel = this[PruebaTable.nivel]?.let { runCatching { Nivel.valueOf(it) }.getOrNull() },
    metadata = this[PruebaTable.metadata]?.let {
        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
    },
    historica = this[PruebaTable.historica]?.let {
        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
    },
    activo = this[PruebaTable.activo]
)

private fun ResultRow.toPruebaCompletaRes(
    json: Json,
    preguntas: List<PreguntaAsignadaResponse>
) = PruebaCompletaRes(
    pruebaId = this[PruebaTable.id].toString(),
    tipoPrueba = this[PruebaTable.tipoPrueba],
    area = this[PruebaTable.area]?.let { runCatching { AreaPrueba.valueOf(it) }.getOrNull() },
    nivel = this[PruebaTable.nivel]?.let { runCatching { Nivel.valueOf(it) }.getOrNull() },
    metadata = this[PruebaTable.metadata]?.let {
        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
    },
    historica = this[PruebaTable.historica]?.let {
        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull()
    },
    activo = this[PruebaTable.activo],
    preguntas = preguntas
)
