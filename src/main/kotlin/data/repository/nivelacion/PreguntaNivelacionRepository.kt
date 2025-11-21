package data.repository.nivelacion

import data.tables.nivelacion.PreguntaNivelacionTable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class PreguntaNivelacionRepository {

    /**
     * Crea una nueva pregunta de nivelación
     */
    fun create(
        habilidad: String,
        dificultad: Int,
        enunciado: String,
        opciones: List<String>,
        respuestaCorrecta: Int,
        explicacion: String?,
        activa: Boolean = true
    ): UUID = transaction {
        val id = PreguntaNivelacionTable.insertAndGetId {
            it[this.habilidad] = habilidad
            it[this.dificultad] = dificultad
            it[this.enunciado] = enunciado
            it[this.opciones] = Json.encodeToString(opciones)
            it[this.respuestaCorrecta] = respuestaCorrecta
            it[this.explicacion] = explicacion
            it[this.activa] = activa
            it[this.fechaCreacion] = OffsetDateTime.now()
        }
        id.value
    }

    /**
     * Obtiene preguntas aleatorias de una habilidad específica
     * @param habilidad La habilidad a evaluar
     * @param cantidad Cantidad de preguntas a obtener (default: 10)
     * @param dificultad Filtro opcional por dificultad (1, 2, 3)
     */
    fun findRandomByHabilidad(
        habilidad: String,
        cantidad: Int = 10,
        dificultad: Int? = null
    ): List<PreguntaNivelacionRow> = transaction {
        val query = PreguntaNivelacionTable
            .selectAll()
            .where {
                (PreguntaNivelacionTable.habilidad eq habilidad) and
                (PreguntaNivelacionTable.activa eq true)
            }

        // Aplicar filtro de dificultad si se especifica
        val filteredQuery = if (dificultad != null) {
            query.andWhere { PreguntaNivelacionTable.dificultad eq dificultad }
        } else {
            query
        }

        filteredQuery
            .orderBy(Random())
            .limit(cantidad)
            .map { toRow(it) }
    }

    /**
     * Busca una pregunta por ID
     */
    fun findById(id: UUID): PreguntaNivelacionRow? = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.id eq id }
            .singleOrNull()
            ?.let { toRow(it) }
    }

    /**
     * Busca múltiples preguntas por IDs
     */
    fun findByIds(ids: List<UUID>): List<PreguntaNivelacionRow> = transaction {
        PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.id inList ids }
            .map { toRow(it) }
    }

    /**
     * Lista todas las preguntas de una habilidad
     */
    fun findByHabilidad(habilidad: String, activasOnly: Boolean = true): List<PreguntaNivelacionRow> = transaction {
        val query = PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.habilidad eq habilidad }

        if (activasOnly) {
            query.andWhere { PreguntaNivelacionTable.activa eq true }
        }

        query.map { toRow(it) }
    }

    /**
     * Actualiza una pregunta existente
     */
    fun update(
        id: UUID,
        enunciado: String? = null,
        opciones: List<String>? = null,
        respuestaCorrecta: Int? = null,
        explicacion: String? = null,
        dificultad: Int? = null,
        activa: Boolean? = null
    ): Int = transaction {
        PreguntaNivelacionTable.update({ PreguntaNivelacionTable.id eq id }) {
            enunciado?.let { value -> it[this.enunciado] = value }
            opciones?.let { value -> it[this.opciones] = Json.encodeToString(value) }
            respuestaCorrecta?.let { value -> it[this.respuestaCorrecta] = value }
            explicacion?.let { value -> it[this.explicacion] = value }
            dificultad?.let { value -> it[this.dificultad] = value }
            activa?.let { value -> it[this.activa] = value }
        }
    }

    /**
     * Desactiva una pregunta (soft delete)
     */
    fun deactivate(id: UUID): Int = transaction {
        PreguntaNivelacionTable.update({ PreguntaNivelacionTable.id eq id }) {
            it[activa] = false
        }
    }

    /**
     * Elimina permanentemente una pregunta
     */
    fun delete(id: UUID): Int = transaction {
        PreguntaNivelacionTable.deleteWhere { PreguntaNivelacionTable.id eq id }
    }

    /**
     * Cuenta las preguntas disponibles por habilidad
     */
    fun countByHabilidad(habilidad: String, activasOnly: Boolean = true): Long = transaction {
        val query = PreguntaNivelacionTable
            .selectAll()
            .where { PreguntaNivelacionTable.habilidad eq habilidad }

        if (activasOnly) {
            query.andWhere { PreguntaNivelacionTable.activa eq true }
        }

        query.count()
    }

    private fun toRow(row: ResultRow): PreguntaNivelacionRow {
        return PreguntaNivelacionRow(
            id = row[PreguntaNivelacionTable.id].value,
            habilidad = row[PreguntaNivelacionTable.habilidad],
            dificultad = row[PreguntaNivelacionTable.dificultad],
            enunciado = row[PreguntaNivelacionTable.enunciado],
            opciones = Json.decodeFromString<List<String>>(row[PreguntaNivelacionTable.opciones]),
            respuestaCorrecta = row[PreguntaNivelacionTable.respuestaCorrecta],
            explicacion = row[PreguntaNivelacionTable.explicacion],
            activa = row[PreguntaNivelacionTable.activa],
            fechaCreacion = row[PreguntaNivelacionTable.fechaCreacion]
        )
    }
}

data class PreguntaNivelacionRow(
    val id: UUID,
    val habilidad: String,
    val dificultad: Int,
    val enunciado: String,
    val opciones: List<String>,
    val respuestaCorrecta: Int,
    val explicacion: String?,
    val activa: Boolean,
    val fechaCreacion: OffsetDateTime
)
