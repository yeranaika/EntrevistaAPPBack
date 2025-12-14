package data.models.admin

import kotlinx.serialization.Serializable

@Serializable
data class InformeGestionRes(
    val totales: TotalesRes,
    val usuarios: List<UsuarioResumenRes>,
    val personasPorCargo: List<PersonasPorCargoRes>,
    val cargosPorArea: List<CargosPorAreaRes>
)

@Serializable
data class TotalesRes(
    val usuariosRegistrados: Int,
    val usuariosActivos: Int,
    val usuariosInactivos: Int,
    val cargosDistintos: Int,
    val areasDistintas: Int,
    val suscripcionesActivas: Int,
    val suscripcionesInactivas: Int,
    val usuariosConPremium: Int,
    val usuariosConFree: Int
)

@Serializable
data class UsuarioResumenRes(
    val usuarioId: String,
    val correo: String,
    val nombre: String? = null,
    val estado: String = "activo",
    val area: String? = null,
    val metaCargo: String? = null,
    val nivel: String? = null,
    val fechaCreacion: String? = null,
    val fechaUltimoLogin: String? = null,
    val planSuscripcion: String? = null,
    val estadoSuscripcion: String? = null,
    val fechaExpiracionSuscripcion: String? = null
)

@Serializable
data class PersonasPorCargoRes(
    val metaCargo: String,
    val area: String?,
    val cantidadPersonas: Int
)

@Serializable
data class CargosPorAreaRes(
    val area: String?,
    val cantidadCargos: Int
)
