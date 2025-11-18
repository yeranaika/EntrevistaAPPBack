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
import java.util.UUID

// ✅ Helper local de transacción suspendida
private suspend fun <T> tx(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

/**
 * Datos mínimos para generar/mostrar resumen de objetivo.
 */
data class OnboardingData(
    val area: String,
    val metaCargo: String,
    val nivel: String
)

class OnboardingRepository {

    // ==========================
    // 1) OBJETIVO / PERFIL
    // ==========================

    /**
     * Solo ACTUALIZA datos existentes:
     *  - profile.area
     *  - profile.nivel_experiencia
     *  - objetivo_carrera activo (nombre_cargo, sector)
     *
     * No crea filas nuevas. Si no existe perfil u objetivo,
     * simplemente no actualiza nada.
     */
    suspend fun guardarObjetivo(
        usuarioId: UUID,
        area: String,
        metaCargo: String,
        nivel: String
    ) = tx {
        // PERFIL_USUARIO: solo update, sin insert
        ProfileTable.update({ ProfileTable.usuarioId eq usuarioId }) {
            it[ProfileTable.area] = area
            it[ProfileTable.nivelExperiencia] = nivel
        }

        // OBJETIVO_CARRERA: solo update del objetivo activo, sin crear nuevo
        ObjetivoCarreraTable.update({
            (ObjetivoCarreraTable.usuarioId eq usuarioId) and
            (ObjetivoCarreraTable.activo eq true)
        }) {
            it[ObjetivoCarreraTable.nombreCargo] = metaCargo
            it[ObjetivoCarreraTable.sector] = area
        }
    }

    /**
     * Recupera área, metaCargo y nivel del usuario.
     * Devuelve null si falta alguno (para responder 400).
     */
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
    // 2) PLAN DE PRÁCTICA (ADMIN / USUARIO)
    // ==========================

    /**
     * ADMIN:
     * Guarda (o reemplaza) el plan de práctica completo de un usuario.
     * - Desactiva planes activos anteriores (activo = false)
     * - Inserta un nuevo plan + pasos
     * - Devuelve el plan tal como quedó guardado (con id en pasos)
     */
    suspend fun guardarPlanParaUsuario(
        usuarioId: UUID,
        req: PlanPracticaRes
    ): PlanPracticaRes = tx {
        // 1) Desactivar planes activos anteriores de ese usuario
        PlanPracticaTable.update({
            (PlanPracticaTable.usuarioId eq usuarioId) and
            (PlanPracticaTable.activo eq true)
        }) {
            it[activo] = false
        }

        // 2) Crear el nuevo plan
        val planIdEntity = PlanPracticaTable.insertAndGetId {
            it[PlanPracticaTable.usuarioId] = usuarioId
            it[area] = req.area
            it[metaCargo] = req.metaCargo
            it[nivel] = req.nivel
            it[activo] = true
        }

        // 3) Insertar los pasos en orden
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

        // 4) Devolver el plan tal como quedó guardado
        PlanPracticaRes(
            area = req.area,
            metaCargo = req.metaCargo,
            nivel = req.nivel,
            pasos = pasosConIds
        )
    }

    /**
     * USUARIO:
     * Obtiene el plan activo del usuario (si existe) con sus pasos.
     * Devuelve null si aún no tiene plan.
     */
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
