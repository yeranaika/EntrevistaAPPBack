package data.repository.nivelacion

import data.tables.nivelacion.PreguntaNivelacionTable
import data.models.PreguntaNivelacionRow
import data.models.PreguntaNivelacionDetalle
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
            row[PreguntaNivelacionTable.tipoBanco] = "NV"
            row[PreguntaNivelacionTable.sector] = area
            row[PreguntaNivelacionTable.nivel] = nivel
            row[PreguntaNivelacionTable.metaCargo] = metaCargo
            row[PreguntaNivelacionTable.tipoPregunta] = "opcion_multiple"
            row[PreguntaNivelacionTable.texto] = texto
            row[PreguntaNivelacionTable.pistas] = pistas
            row[PreguntaNivelacionTable.configRespuesta] = config
            row[PreguntaNivelacionTable.activa] = true
            row[PreguntaNivelacionTable.fechaCreacion] = OffsetDateTime.now()
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
            row[PreguntaNivelacionTable.tipoBanco] = "NV"
            row[PreguntaNivelacionTable.sector] = area
            row[PreguntaNivelacionTable.nivel] = nivel
            row[PreguntaNivelacionTable.metaCargo] = metaCargo
            row[PreguntaNivelacionTable.tipoPregunta] = "abierta"
            row[PreguntaNivelacionTable.texto] = texto
            row[PreguntaNivelacionTable.pistas] = pistas
            row[PreguntaNivelacionTable.configRespuesta] = config
            row[PreguntaNivelacionTable.activa] = true
            row[PreguntaNivelacionTable.fechaCreacion] = OffsetDateTime.now()
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

    /**
     * Buscar pregunta por ID y retornar como PreguntaNivelacionDetalle
     * Usado por rutas de administración
     */
    fun findDetalleById(id: UUID): data.models.PreguntaNivelacionDetalle? = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.id eq id }
            .singleOrNull()
            ?.let { toDetalle(it) }
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
            it[PreguntaNivelacionTable.activa] = false
        }
    }

    /**
     * Actualizar una pregunta existente
     */
    fun update(
        id: UUID,
        enunciado: String,
        opciones: List<String>,
        respuestaCorrecta: Int,
        explicacion: String?,
        dificultad: Int,
        activa: Boolean
    ): Int = transaction {
        require(opciones.isNotEmpty()) { "Debe haber al menos una opción" }
        require(respuestaCorrecta in opciones.indices) { "respuestaCorrecta fuera de rango" }
        require(dificultad in 1..3) { "Dificultad debe ser 1, 2, o 3" }

        val config = buildConfigOpcionMultiple(opciones, respuestaCorrecta)

        PreguntaNivelacionTable.update({ PreguntaNivelacionTable.id eq id }) {
            it[PreguntaNivelacionTable.texto] = enunciado
            it[PreguntaNivelacionTable.configRespuesta] = config
            it[PreguntaNivelacionTable.pistas] = explicacion
            it[PreguntaNivelacionTable.activa] = activa
            // Actualizar nivel basado en dificultad
            it[PreguntaNivelacionTable.nivel] = when(dificultad) {
                1 -> "jr"
                2 -> "mid"
                3 -> "sr"
                else -> "jr"
            }
        }
    }

    // ==========================
    //  NUEVOS MÉTODOS PARA SISTEMA DE ONBOARDING
    //  (compatibles con migración 007)
    // ==========================

    /**
     * Crear pregunta con el nuevo esquema simplificado
     * Usado por el sistema de onboarding y tests de nivelación automáticos
     */
    fun createSimple(
        habilidad: String,
        dificultad: Int,
        enunciado: String,
        opciones: List<String>,
        respuestaCorrecta: Int,
        explicacion: String? = null,
        activa: Boolean = true
    ): UUID = transaction {
        require(dificultad in 1..3) { "Dificultad debe ser 1 (básico), 2 (intermedio) o 3 (avanzado)" }
        require(opciones.isNotEmpty()) { "Debe haber al menos una opción" }
        require(respuestaCorrecta in opciones.indices) { "respuestaCorrecta fuera de rango" }

        val nivelTexto = when(dificultad) {
            1 -> "jr"
            2 -> "mid"
            3 -> "sr"
            else -> "jr"
        }

        val config = buildConfigOpcionMultiple(opciones, respuestaCorrecta)

        PreguntaNivelacionTable.insertAndGetId { row ->
            row[PreguntaNivelacionTable.tipoBanco] = "NV"
            row[PreguntaNivelacionTable.sector] = habilidad
            row[PreguntaNivelacionTable.nivel] = nivelTexto
            row[PreguntaNivelacionTable.metaCargo] = null
            row[PreguntaNivelacionTable.tipoPregunta] = "opcion_multiple"
            row[PreguntaNivelacionTable.texto] = enunciado
            row[PreguntaNivelacionTable.pistas] = explicacion
            row[PreguntaNivelacionTable.configRespuesta] = config
            row[PreguntaNivelacionTable.activa] = activa
            row[PreguntaNivelacionTable.fechaCreacion] = OffsetDateTime.now()
        }.value
    }

    /**
     * Buscar preguntas por habilidad (devuelve PreguntaNivelacionDetalle)
     * Usado por rutas de administración
     */
    fun findByHabilidad(habilidad: String, activasOnly: Boolean = true): List<data.models.PreguntaNivelacionDetalle> = transaction {
        val query = PreguntaNivelacionTable
            .selectAll()
            .where {
                (PreguntaNivelacionTable.tipoBanco eq "NV") and
                (PreguntaNivelacionTable.sector eq habilidad)
            }

        val finalQuery = if (activasOnly) {
            query.andWhere { PreguntaNivelacionTable.activa eq true }
        } else {
            query
        }

        finalQuery
            .orderBy(PreguntaNivelacionTable.fechaCreacion, SortOrder.DESC)
            .map { toDetalle(it) }
    }

    /**
     * Cuenta preguntas activas por habilidad
     */
    fun countByHabilidad(habilidad: String): Long = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where {
                (PreguntaNivelacionTable.tipoBanco eq "NV") and
                (PreguntaNivelacionTable.sector eq habilidad) and
                (PreguntaNivelacionTable.activa eq true)
            }
            .count()
    }

    /**
     * Obtiene preguntas aleatorias por habilidad, mezclando dificultades
     * Útil para generar tests balanceados
     */
    fun findRandomByHabilidad(
        habilidad: String,
        cantidad: Int,
        mezclarDificultades: Boolean = true
    ): List<PreguntaNivelacionDetalle> = transaction {
        if (mezclarDificultades && cantidad >= 10) {
            // Mezcla balanceada: 40% básicas, 40% intermedias, 20% avanzadas
            val basicas = (cantidad * 0.4).toInt()
            val intermedias = (cantidad * 0.4).toInt()
            val avanzadas = cantidad - basicas - intermedias

            val preguntasBasicas = findByHabilidadYNivel(habilidad, "jr", basicas)
            val preguntasIntermedias = findByHabilidadYNivel(habilidad, "mid", intermedias)
            val preguntasAvanzadas = findByHabilidadYNivel(habilidad, "sr", avanzadas)

            (preguntasBasicas + preguntasIntermedias + preguntasAvanzadas).shuffled()
        } else {
            // Selección aleatoria sin mezcla específica
            PreguntaNivelacionTable
                .selectAll()
                .where {
                    (PreguntaNivelacionTable.tipoBanco eq "NV") and
                    (PreguntaNivelacionTable.sector eq habilidad) and
                    (PreguntaNivelacionTable.activa eq true) and
                    (PreguntaNivelacionTable.tipoPregunta eq "opcion_multiple")
                }
                .orderBy(Random())
                .limit(cantidad)
                .map { toDetalle(it) }
        }
    }

    /**
     * Método auxiliar para obtener preguntas por habilidad y nivel
     */
    private fun findByHabilidadYNivel(
        habilidad: String,
        nivel: String,
        cantidad: Int
    ): List<PreguntaNivelacionDetalle> = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where {
                (PreguntaNivelacionTable.tipoBanco eq "NV") and
                (PreguntaNivelacionTable.sector eq habilidad) and
                (PreguntaNivelacionTable.nivel eq nivel) and
                (PreguntaNivelacionTable.activa eq true) and
                (PreguntaNivelacionTable.tipoPregunta eq "opcion_multiple")
            }
            .orderBy(Random())
            .limit(cantidad)
            .map { toDetalle(it) }
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

    /**
     * Convierte un ResultRow a PreguntaNivelacionDetalle (con respuesta_correcta como índice)
     */
    private fun toDetalle(row: ResultRow): PreguntaNivelacionDetalle {
        val configStr = row[PreguntaNivelacionTable.configRespuesta]
        val nivel = row[PreguntaNivelacionTable.nivel]

        var opciones: List<String> = emptyList()
        var respuestaCorrecta: Int = 0

        if (!configStr.isNullOrBlank()) {
            val jsonConfig = json.parseToJsonElement(configStr).jsonObject

            // Extraer opciones
            opciones = jsonConfig["opciones"]
                ?.jsonArray
                ?.map { it.jsonObject["texto"]!!.jsonPrimitive.content }
                ?: emptyList()

            // Convertir respuesta correcta de letra (A/B/C) a índice (0/1/2)
            val correctaId = jsonConfig["respuesta_correcta"]?.jsonPrimitive?.content
            respuestaCorrecta = when (correctaId) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                else -> 0
            }
        }

        // Convertir nivel (jr/mid/sr) a dificultad (1/2/3)
        val dificultad = when (nivel) {
            "jr" -> 1
            "mid" -> 2
            "sr" -> 3
            else -> 1
        }

        return PreguntaNivelacionDetalle(
            id = row[PreguntaNivelacionTable.id].value.toString(),
            habilidad = row[PreguntaNivelacionTable.sector] ?: "",
            dificultad = dificultad,
            enunciado = row[PreguntaNivelacionTable.texto],
            opciones = opciones,
            respuestaCorrecta = respuestaCorrecta,
            explicacion = row[PreguntaNivelacionTable.pistas],
            activa = row[PreguntaNivelacionTable.activa],
            fechaCreacion = row[PreguntaNivelacionTable.fechaCreacion].toString()
        )
    }
}
