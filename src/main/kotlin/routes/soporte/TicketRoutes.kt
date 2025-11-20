package routes.soporte

import data.repository.soporte.TicketRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import routes.admin.ErrorResponse
import java.util.UUID

fun Route.ticketRoutes(ticketRepository: TicketRepository) {

    authenticate("auth-jwt") {
        /**
         * POST /soporte/tickets
         * Crea un nuevo ticket de soporte
         */
        post("/soporte/tickets") {
            // Verificar autenticación
            val principal = call.principal<JWTPrincipal>()
            val usuarioId = principal?.subject

            if (usuarioId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("No autenticado"))
                return@post
            }

            try {
                val req = call.receive<CrearTicketReq>()

                // Validar campos
                if (req.mensaje.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("El mensaje no puede estar vacío"))
                    return@post
                }

                // Validar categoría
                val categoriasValidas = listOf("bug", "sugerencia", "consulta", "otro")
                if (req.categoria.lowercase() !in categoriasValidas) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("Categoría inválida. Valores permitidos: bug, sugerencia, consulta, otro")
                    )
                    return@post
                }

                // Crear ticket
                val ticket = ticketRepository.create(
                    usuarioId = UUID.fromString(usuarioId),
                    mensaje = req.mensaje.trim(),
                    categoria = req.categoria.lowercase().trim()
                )

                if (ticket == null) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error al crear ticket"))
                    return@post
                }

                // Responder con el ticket creado
                call.respond(
                    HttpStatusCode.Created,
                    TicketCreadoRes(
                        message = "Ticket creado exitosamente",
                        ticket = TicketRes(
                            ticketId = ticket.ticketId.toString(),
                            usuarioId = ticket.usuarioId.toString(),
                            nombreUsuario = ticket.nombreUsuario,
                            correoUsuario = ticket.correoUsuario,
                            mensaje = ticket.mensaje,
                            categoria = ticket.categoria,
                            estado = ticket.estado,
                            fechaCreacion = ticket.fechaCreacion.toString(),
                            fechaActualizacion = ticket.fechaActualizacion?.toString()
                        )
                    )
                )

            } catch (e: Exception) {
                call.application.environment.log.error("Error al crear ticket", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error interno del servidor"))
            }
        }

        /**
         * GET /soporte/tickets
         * Obtiene todos los tickets del usuario autenticado
         */
        get("/soporte/tickets") {
            // Verificar autenticación
            val principal = call.principal<JWTPrincipal>()
            val usuarioId = principal?.subject

            if (usuarioId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("No autenticado"))
                return@get
            }

            try {
                val tickets = ticketRepository.findByUsuarioId(UUID.fromString(usuarioId))

                val ticketsRes = tickets.map { ticket ->
                    TicketRes(
                        ticketId = ticket.ticketId.toString(),
                        usuarioId = ticket.usuarioId.toString(),
                        nombreUsuario = ticket.nombreUsuario,
                        correoUsuario = ticket.correoUsuario,
                        mensaje = ticket.mensaje,
                        categoria = ticket.categoria,
                        estado = ticket.estado,
                        fechaCreacion = ticket.fechaCreacion.toString(),
                        fechaActualizacion = ticket.fechaActualizacion?.toString()
                    )
                }

                call.respond(HttpStatusCode.OK, ticketsRes)

            } catch (e: Exception) {
                call.application.environment.log.error("Error al obtener tickets", e)
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Error interno del servidor"))
            }
        }
    }
}
