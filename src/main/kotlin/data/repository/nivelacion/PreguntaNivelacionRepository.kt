package data.repository.nivelacion

import data.tables.nivelacion.PreguntaNivelacionTable
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class PreguntaNivelacionRepository(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    // ==========================
    //  CREAR PREGUNTAS (ADMIN)
    // ==========================

    /**
     * Crea una pregunta de NIVELACIÓN de opción múltiple (tipo_banco = 'NV')
     * NOTA: debe ser usada solo desde rutas de ADMIN (se valida el rol en la route).
     */
    fun createOpcionMultipleNivelacion(
        area: String,
        nivel: String,
        metaCargo: String?,
        texto: String,
        opciones: List<String>,
        indiceCorrecta: Int,
        pistas: String? = null
    ): UUID = transaction {
        require(opciones.isNotEmpty()) { "Debe haber al menos una opción" }
        require(indiceCorrecta in opciones.indices) { "indiceCorrecta fuera de rango" }

        val config = buildConfigOpcionMultiple(opciones, indiceCorrecta)

        PreguntaNivelacionTable.insertAndGetId { row ->
            row[tipoBanco] = "NV"
            row[sector] = area
            row[PreguntaNivelacionTable.nivel] = nivel
            row[metaCargo] = metaCargo
            row[tipoPregunta] = "opcion_multiple"
            row[PreguntaNivelacionTable.texto] = texto
            row[pistas] = pistas
            row[configRespuesta] = config
            row[activa] = true
            row[fechaCreacion] = OffsetDateTime.now()
        }.value
    }

    /**
     * Crea una pregunta de NIVELACIÓN ABIERTA (respuesta libre).
     * Por ejemplo usada luego en tests de aprendizaje.
     */
    fun createAbiertaNivelacion(
        area: String,
        nivel: String,
        metaCargo: String?,
        texto: String,
        criteriosJson: String? = null, // ej {"min_palabras":30,...}
        pistas: String? = null
    ): UUID = transaction {
        val config = buildConfigAbierta(criteriosJson)

        PreguntaNivelacionTable.insertAndGetId { row ->
            row[tipoBanco] = "NV"
            row[sector] = area
            row[PreguntaNivelacionTable.nivel] = nivel
            row[metaCargo] = metaCargo
            row[tipoPregunta] = "abierta"
            row[PreguntaNivelacionTable.texto] = texto
            row[pistas] = pistas
            row[configRespuesta] = config
            row[activa] = true
            row[fechaCreacion] = OffsetDateTime.now()
        }.value
    }

    // ==========================
    //  OBTENER PREGUNTAS
    // ==========================

    /**
     * Preguntas aleatorias de nivelación por área + nivel + (opcional) cargo meta.
     * Por defecto trae SOLO opción múltiple (útil para test de nivelación con puntaje automático).
     */
    fun findRandomNivelacion(
        area: String,
        nivel: String,
        metaCargo: String? = null,
        cantidad: Int = 10,
        soloOpcionMultiple: Boolean = true
    ): List<PreguntaNivelacionRow> = transaction {
        val base = PreguntaNivelacionTable
            .selectAll()
            .where {
                (PreguntaNivelacionTable.tipoBanco eq "NV") and
                (PreguntaNivelacionTable.sector eq area) and
                (PreguntaNivelacionTable.nivel eq nivel) and
                (PreguntaNivelacionTable.activa eq true)
            }

        val conCargo = if (metaCargo.isNullOrBlank()) {
            base
        } else {
            base.andWhere { PreguntaNivelacionTable.metaCargo eq metaCargo }
        }

        val finalQuery = if (soloOpcionMultiple) {
            conCargo.andWhere { PreguntaNivelacionTable.tipoPregunta eq "opcion_multiple" }
        } else {
            conCargo
        }

        finalQuery
            .orderBy(Random())
            .limit(cantidad)
            .map { toRow(it) }
    }

    fun findById(id: UUID): PreguntaNivelacionRow? = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.id eq id }
            .singleOrNull()
            ?.let { toRow(it) }
    }

    fun findByIds(ids: List<UUID>): List<PreguntaNivelacionRow> = transaction {
        if (ids.isEmpty()) return@transaction emptyList()
        PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.id inList ids }
            .map { toRow(it) }
    }

    /**
     * Soft delete (desactiva la pregunta)
     */
    fun deactivate(id: UUID): Int = transaction {
        PreguntaNivelacionTable.update({ PreguntaNivelacionTable.id eq id }) {
            it[activa] = false
        }
    }

    // ==========================
    //  HELPERS
    // ==========================

    private fun buildConfigOpcionMultiple(
        opciones: List<String>,
        indiceCorrecta: Int
    ): String {
        val config = buildJsonObject {
            put("tipo", "opcion_multiple")
            putJsonArray("opciones") {
                opciones.forEachIndexed { idx, texto ->
                    add(
                        buildJsonObject {
                            put("id", ('A' + idx).toString())
                            put("texto", texto)
                        }
                    )
                }
            }
            put("respuesta_correcta", ('A' + indiceCorrecta).toString())
        }
        return config.toString()
    }

    private fun buildConfigAbierta(criteriosJson: String?): String {
        // Si te mandan criterios como JSON crudo, lo usamos tal cual.
        val criterios = criteriosJson?.let { json.parseToJsonElement(it) } ?: buildJsonObject { }
        val config = buildJsonObject {
            put("tipo", "abierta")
            put("criterios", criterios)
        }
        return config.toString()
    }

    private fun toRow(row: ResultRow): PreguntaNivelacionRow {
        val configStr = row[PreguntaNivelacionTable.configRespuesta]
        val tipoPregunta = row[PreguntaNivelacionTable.tipoPregunta]

        var opciones: List<String>? = null
        var correctaId: String? = null

        if (!configStr.isNullOrBlank()) {
            val jsonConfig = json.parseToJsonElement(configStr).jsonObject
            if (tipoPregunta == "opcion_multiple") {
                opciones = jsonConfig["opciones"]
                    ?.jsonArray
                    ?.map { it.jsonObject["texto"]!!.jsonPrimitive.content }
                correctaId = jsonConfig["respuesta_correcta"]?.jsonPrimitive?.content
            }
        }

        return PreguntaNivelacionRow(
            id = row[PreguntaNivelacionTable.id].value,
            area = row[PreguntaNivelacionTable.sector],
            nivel = row[PreguntaNivelacionTable.nivel],
            metaCargo = row[PreguntaNivelacionTable.metaCargo],
            tipoPregunta = tipoPregunta,
            enunciado = row[PreguntaNivelacionTable.texto],
            opciones = opciones,
            respuestaCorrectaId = correctaId,
            pistas = row[PreguntaNivelacionTable.pistas],
            activa = row[PreguntaNivelacionTable.activa],
            fechaCreacion = row[PreguntaNivelacionTable.fechaCreacion]
        )
    }
}
