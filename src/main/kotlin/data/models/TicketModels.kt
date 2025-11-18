package data.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID

/**
 * Modelo de Ticket (para uso interno)
 */
data class Ticket(
    val ticketId: UUID,
    val usuarioId: UUID,
    val mensaje: String,
    val categoria: String,
    val estado: String,
    val fechaCreacion: LocalDateTime,
    val fechaActualizacion: LocalDateTime?
)

/**
 * Ticket con información del usuario (para respuestas)
 */
data class TicketConUsuario(
    val ticketId: UUID,
    val usuarioId: UUID,
    val nombreUsuario: String?,
    val correoUsuario: String,
    val mensaje: String,
    val categoria: String,
    val estado: String,
    val fechaCreacion: LocalDateTime,
    val fechaActualizacion: LocalDateTime?
)
