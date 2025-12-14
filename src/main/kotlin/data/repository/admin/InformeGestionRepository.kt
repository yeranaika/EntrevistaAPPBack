package data.repository.admin

import data.models.admin.*
import data.tables.usuarios.UsuarioTable
import data.tables.usuarios.ObjetivoCarreraTable
import data.tables.cuestionario.PlanPracticaTable
import data.tables.billing.SuscripcionTable
import data.tables.sesiones.SesionEntrevistaTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId

class InformeGestionRepository {

    suspend fun obtenerInformeGestion(): InformeGestionRes =
        newSuspendedTransaction(Dispatchers.IO) {

            val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

            // 1) Usuarios + su objetivo (si tiene plan de práctica) + suscripción
            val usuarios = (UsuarioTable leftJoin PlanPracticaTable leftJoin SuscripcionTable)
                .selectAll()
                .map { row ->
                    UsuarioResumenRes(
                        usuarioId = row[UsuarioTable.usuarioId].toString(),
                        correo = row[UsuarioTable.correo],
                        nombre = row[UsuarioTable.nombre],
                        estado = row[UsuarioTable.estado],
                        area = row.getOrNull(PlanPracticaTable.area),
                        metaCargo = row.getOrNull(PlanPracticaTable.metaCargo),
                        nivel = row.getOrNull(PlanPracticaTable.nivel),
                        fechaCreacion = row[UsuarioTable.fechaCreacion].format(dateFormatter),
                        fechaUltimoLogin = row[UsuarioTable.fechaUltimoLogin]?.format(dateFormatter),
                        planSuscripcion = row.getOrNull(SuscripcionTable.plan),
                        estadoSuscripcion = row.getOrNull(SuscripcionTable.estado),
                        fechaExpiracionSuscripcion = row.getOrNull(SuscripcionTable.fechaExpiracion)?.toString()
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

            // 4) Calcular métricas de usuarios
            val usuariosActivos = usuarios.count { it.estado == "activo" }
            val usuariosInactivos = usuarios.count { it.estado == "inactivo" }



            // Métricas de suscripciones
            val suscripcionesActivas = usuarios.count { it.estadoSuscripcion == "activa" }
            val suscripcionesInactivas = usuarios.count { it.estadoSuscripcion != null && it.estadoSuscripcion != "activa" }
            val usuariosConPremium = usuarios.count { it.planSuscripcion != null && it.planSuscripcion != "free" }
            val usuariosConFree = usuarios.count { it.planSuscripcion == "free" || it.planSuscripcion == null }

            val totales = TotalesRes(
                usuariosRegistrados = usuarios.size,
                usuariosActivos = usuariosActivos,
                usuariosInactivos = usuariosInactivos,


                cargosDistintos = personasPorCargo.size,   // nº de grupos metaCargo/area
                areasDistintas = cargosPorArea.size,
                suscripcionesActivas = suscripcionesActivas,
                suscripcionesInactivas = suscripcionesInactivas,
                usuariosConPremium = usuariosConPremium,
                usuariosConFree = usuariosConFree
            )

            InformeGestionRes(
                totales = totales,
                usuarios = usuarios,
                personasPorCargo = personasPorCargo,
                cargosPorArea = cargosPorArea
            )
        }

    // Obtener objetivos de carrera para el Excel
    suspend fun obtenerObjetivosCarrera(): List<ObjetivoCarreraExcel> =
        newSuspendedTransaction(Dispatchers.IO) {
            (UsuarioTable innerJoin ObjetivoCarreraTable)
                .selectAll()
                .map { row ->
                    ObjetivoCarreraExcel(
                        correo = row[UsuarioTable.correo],
                        nombre = row[UsuarioTable.nombre],
                        nombreCargo = row[ObjetivoCarreraTable.nombreCargo],
                        sector = row[ObjetivoCarreraTable.sector],
                        activo = row[ObjetivoCarreraTable.activo]
                    )
                }
        }

    // TODO: Excel export - Temporarily disabled due to type mismatch issues
    // suspend fun obtenerDatosExcel(): List<UsuarioExcelRow> = ...
}