package routes.soporte

import kotlinx.serialization.Serializable

// ============================================
// REQUEST DTOs
// ============================================

@Serializable
data class CrearTicketReq(
    val mensaje: String,
    val categoria: String
)

// ============================================
// RESPONSE DTOs
// ============================================

@Serializable
data class TicketRes(
    val ticketId: String,
    val usuarioId: String,
    val nombreUsuario: String?,
    val correoUsuario: String,
    val mensaje: String,
    val categoria: String,
    val estado: String,
    val fechaCreacion: String,
    val fechaActualizacion: String?
)

@Serializable
data class TicketCreadoRes(
    val message: String,
    val ticket: TicketRes
)
