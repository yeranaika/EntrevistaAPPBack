package data.tables.soporte

import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object TicketTable : Table("ticket") {

    val ticketId = uuid("ticket_id")
    val usuarioId = uuid("usuario_id").references(UsuarioTable.usuarioId)
    val mensaje = text("mensaje")
    val categoria = varchar("categoria", 50)
    val estado = varchar("estado", 20).default("abierto")
    val fechaCreacion = datetime("fecha_creacion").clientDefault { LocalDateTime.now() }
    val fechaActualizacion = datetime("fecha_actualizacion").nullable()

    override val primaryKey = PrimaryKey(ticketId, name = "ticket_pk")
}
