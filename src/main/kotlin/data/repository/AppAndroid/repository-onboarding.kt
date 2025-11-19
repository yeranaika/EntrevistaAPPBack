package data.repository.AppAndroid

import data.models.usuarios.PlanPracticaPasoDto
import data.models.usuarios.PlanPracticaRes
import data.tables.usuarios.ProfileTable
import data.tables.usuarios.ObjetivoCarreraTable
import data.tables.cuestionario.PlanPracticaTable
import data.tables.cuestionario.PlanPracticaPasoTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime      // 👈 IMPORT CORRECTO
import java.time.ZoneOffset         // 👈 IMPORT PARA UTC
import java.util.UUID

// Helper de transacción suspendida
private suspend fun <T> tx(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

/** Datos mínimos que usamos en Android para decidir onboarding. */
data class OnboardingData(
    val area: String,
    val metaCargo: String,
    val nivel: String
)

class OnboardingRepository {

    // ==========================
    // 1) OBJETIVO / PERFIL
    // ==========================

    suspend fun guardarObjetivo(
        usuarioId: UUID,
        area: String,
        metaCargo: String,
        nivel: String
    ) = tx {
        // 👇 Momento actual para fecha_actualizacion (tipo OffsetDateTime)
        val ahora = OffsetDateTime.now(ZoneOffset.UTC)

        // ---- PERFIL_USUARIO: UPDATE, si no existe → INSERT ----
        val filasPerfil = ProfileTable.update({
            ProfileTable.usuarioId eq usuarioId
        }) {
            it[ProfileTable.area] = area
            it[ProfileTable.nivelExperiencia] = nivel
            it[ProfileTable.fechaActualizacion] = ahora   // ✅ tipo correcto
        }

        if (filasPerfil == 0) {
            ProfileTable.insert {
                it[ProfileTable.perfilId] = UUID.randomUUID()
                it[ProfileTable.usuarioId] = usuarioId
                it[ProfileTable.area] = area
                it[ProfileTable.nivelExperiencia] = nivel
                it[ProfileTable.fechaActualizacion] = ahora   // ✅ también aquí
            }
        }

        // ---- OBJETIVO_CARRERA: UPDATE, si no existe → INSERT ----
        val filasObjetivo = ObjetivoCarreraTable.update({
            (ObjetivoCarreraTable.usuarioId eq usuarioId) and
                    (ObjetivoCarreraTable.activo eq true)
        }) {
            it[ObjetivoCarreraTable.nombreCargo] = metaCargo
            it[ObjetivoCarreraTable.sector] = area
        }

        if (filasObjetivo == 0) {
            ObjetivoCarreraTable.insert {
                it[ObjetivoCarreraTable.usuarioId] = usuarioId
                it[ObjetivoCarreraTable.nombreCargo] = metaCargo
                it[ObjetivoCarreraTable.sector] = area
                // activo usa el default true de la BD
            }
        }
    }

    suspend fun obtenerOnboarding(usuarioId: UUID): OnboardingData? = tx {
        val perfil = ProfileTable
            .selectAll()
            .where { ProfileTable.usuarioId eq usuarioId }
            .singleOrNull()

        val objetivo = ObjetivoCarreraTable
            .selectAll()
            .where {
                (ObjetivoCarreraTable.usuarioId eq usuarioId) and
                        (ObjetivoCarreraTable.activo eq true)
            }
            .singleOrNull()

        if (perfil == null || objetivo == null) return@tx null

        val area = perfil[ProfileTable.area] ?: return@tx null
        val nivel = perfil[ProfileTable.nivelExperiencia] ?: "jr"
        val metaCargo = objetivo[ObjetivoCarreraTable.nombreCargo]

        OnboardingData(
            area = area,
            metaCargo = metaCargo,
            nivel = nivel
        )
    }

    // ==========================
    // 2) PLAN DE PRÁCTICA
    // ==========================

    suspend fun guardarPlanParaUsuario(
        usuarioId: UUID,
        req: PlanPracticaRes
    ): PlanPracticaRes = tx {
        // Desactivar planes anteriores
        PlanPracticaTable.update({
            (PlanPracticaTable.usuarioId eq usuarioId) and
                    (PlanPracticaTable.activo eq true)
        }) {
            it[activo] = false
        }

        // Crear nuevo plan
        val planIdEntity = PlanPracticaTable.insertAndGetId {
            it[PlanPracticaTable.usuarioId] = usuarioId
            it[area] = req.area
            it[metaCargo] = req.metaCargo
            it[nivel] = req.nivel
            it[activo] = true
        }

        // Insertar pasos
        val pasosConIds = req.pasos.mapIndexed { index, paso ->
            val pasoIdEntity = PlanPracticaPasoTable.insertAndGetId {
                it[planId] = planIdEntity
                it[orden] = index + 1
                it[titulo] = paso.titulo
                it[descripcion] = paso.descripcion
                it[sesionesPorSemana] = paso.sesionesPorSemana
            }

            paso.copy(id = pasoIdEntity.value.toString())
        }

        PlanPracticaRes(
            area = req.area,
            metaCargo = req.metaCargo,
            nivel = req.nivel,
            pasos = pasosConIds
        )
    }

    suspend fun obtenerPlanUsuario(usuarioId: UUID): PlanPracticaRes? = tx {
        val planRow = PlanPracticaTable
            .selectAll()
            .where {
                (PlanPracticaTable.usuarioId eq usuarioId) and
                        (PlanPracticaTable.activo eq true)
            }
            .singleOrNull() ?: return@tx null

        val pasosRows = PlanPracticaPasoTable
            .selectAll()
            .where { PlanPracticaPasoTable.planId eq planRow[PlanPracticaTable.id] }
            .orderBy(PlanPracticaPasoTable.orden to SortOrder.ASC)

        val pasos = pasosRows.map { row ->
            PlanPracticaPasoDto(
                id = row[PlanPracticaPasoTable.id].value.toString(),
                titulo = row[PlanPracticaPasoTable.titulo],
                descripcion = row[PlanPracticaPasoTable.descripcion],
                sesionesPorSemana = row[PlanPracticaPasoTable.sesionesPorSemana]
            )
        }

        PlanPracticaRes(
            area = planRow[PlanPracticaTable.area] ?: "",
            metaCargo = planRow[PlanPracticaTable.metaCargo] ?: "",
            nivel = planRow[PlanPracticaTable.nivel] ?: "",
            pasos = pasos
        )
    }
}
