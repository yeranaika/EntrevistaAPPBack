package data.repository.cuestionario

import data.tables.cuestionario.PlanPracticaPasoTable
import data.tables.cuestionario.PlanPracticaTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class PlanPracticaRepository {

    fun findPlanByUser(userId: UUID): PlanPracticaRow? = transaction {
        PlanPracticaTable
            .selectAll()
            .where { (PlanPracticaTable.usuarioId eq userId) and (PlanPracticaTable.activo eq true) }
            .singleOrNull()
            ?.let { toPlanRow(it) }
    }

    fun findPasosByPlan(planId: UUID): List<PlanPracticaPasoRow> = transaction {
        PlanPracticaPasoTable
            .selectAll()
            .where { PlanPracticaPasoTable.planId eq planId }
            .orderBy(PlanPracticaPasoTable.orden)
            .map { toPasoRow(it) }
    }

    fun createPlan(userId: UUID, area: String?, metaCargo: String?, nivel: String?): UUID = transaction {
        PlanPracticaTable.insertAndGetId {
            it[this.usuarioId] = userId
            it[this.area] = area
            it[this.metaCargo] = metaCargo
            it[this.nivel] = nivel
            it[this.activo] = true
        }.value
    }

    fun createPaso(planId: UUID, orden: Int, titulo: String, descripcion: String?, sesiones: Int?) = transaction {
        val planEntityId = PlanPracticaTable.selectAll().where { PlanPracticaTable.id eq planId }.single()[PlanPracticaTable.id]

        PlanPracticaPasoTable.insert {
            it[this.planId] = planEntityId
            it[this.orden] = orden
            it[this.titulo] = titulo
            it[this.descripcion] = descripcion
            it[this.sesionesPorSemana] = sesiones
        }
    }

    private fun toPlanRow(row: ResultRow): PlanPracticaRow {
        return PlanPracticaRow(
            id = row[PlanPracticaTable.id].value,
            usuarioId = row[PlanPracticaTable.usuarioId],
            area = row[PlanPracticaTable.area],
            metaCargo = row[PlanPracticaTable.metaCargo],
            nivel = row[PlanPracticaTable.nivel],
            activo = row[PlanPracticaTable.activo]
        )
    }

    private fun toPasoRow(row: ResultRow): PlanPracticaPasoRow {
        return PlanPracticaPasoRow(
            id = row[PlanPracticaPasoTable.id].value,
            planId = row[PlanPracticaPasoTable.planId].value,
            orden = row[PlanPracticaPasoTable.orden],
            titulo = row[PlanPracticaPasoTable.titulo],
            descripcion = row[PlanPracticaPasoTable.descripcion],
            sesionesPorSemana = row[PlanPracticaPasoTable.sesionesPorSemana]
        )
    }
}

data class PlanPracticaRow(
    val id: UUID,
    val usuarioId: UUID,
    val area: String?,
    val metaCargo: String?,
    val nivel: String?,
    val activo: Boolean
)

data class PlanPracticaPasoRow(
    val id: UUID,
    val planId: UUID,
    val orden: Int,
    val titulo: String,
    val descripcion: String?,
    val sesionesPorSemana: Int?
)
