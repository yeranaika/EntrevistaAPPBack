package data.repository.admin

import data.models.admin.*
import data.tables.usuarios.UsuarioTable
import data.tables.cuestionario.PlanPracticaTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class InformeGestionRepository {

    suspend fun obtenerInformeGestion(): InformeGestionRes =
        newSuspendedTransaction(Dispatchers.IO) {

            // 1) Usuarios + su objetivo (si tiene plan de práctica)
            val usuarios = (UsuarioTable leftJoin PlanPracticaTable)
                .select(
                    UsuarioTable.usuarioId,
                    UsuarioTable.correo,
                    UsuarioTable.nombre,
                    PlanPracticaTable.area,
                    PlanPracticaTable.metaCargo,
                    PlanPracticaTable.nivel
                )
                .map { row ->
                    UsuarioResumenRes(
                        usuarioId = row[UsuarioTable.usuarioId].toString(),
                        correo = row[UsuarioTable.correo],
                        nombre = row[UsuarioTable.nombre],
                        area = row.getOrNull(PlanPracticaTable.area),
                        metaCargo = row.getOrNull(PlanPracticaTable.metaCargo),
                        nivel = row.getOrNull(PlanPracticaTable.nivel)
                    )
                }

            // 2) Personas por cargo (cantidad de planes activos por cargo/área)
            val countPersonas = PlanPracticaTable.usuarioId.count().alias("cantidad_personas")

            val personasPorCargo = PlanPracticaTable
                .select(
                    PlanPracticaTable.metaCargo,
                    PlanPracticaTable.area,
                    countPersonas
                )
                .where { PlanPracticaTable.activo eq true }
                .groupBy(PlanPracticaTable.metaCargo, PlanPracticaTable.area)
                .map { row ->
                    PersonasPorCargoRes(
                        metaCargo = row[PlanPracticaTable.metaCargo] ?: "Sin cargo",
                        area = row[PlanPracticaTable.area],
                        cantidadPersonas = row[countPersonas].toInt()   // count() -> Long
                    )
                }

            // 3) Cargos por área (cantidad de cargos/planes activos por área)
            val countCargos = PlanPracticaTable.metaCargo.count().alias("cantidad_cargos")

            val cargosPorArea = PlanPracticaTable
                .select(
                    PlanPracticaTable.area,
                    countCargos
                )
                .where { PlanPracticaTable.activo eq true }
                .groupBy(PlanPracticaTable.area)
                .map { row ->
                    CargosPorAreaRes(
                        area = row[PlanPracticaTable.area],
                        cantidadCargos = row[countCargos].toInt()
                    )
                }

            val totales = TotalesRes(
                usuariosRegistrados = usuarios.size,
                cargosDistintos = personasPorCargo.size,   // nº de grupos metaCargo/area
                areasDistintas = cargosPorArea.size
            )

            InformeGestionRes(
                totales = totales,
                usuarios = usuarios,
                personasPorCargo = personasPorCargo,
                cargosPorArea = cargosPorArea
            )
        }
}