package data.repository.soporte

import data.models.TicketConUsuario
import data.tables.soporte.TicketTable
import data.tables.usuarios.UsuarioTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

interface TicketRepository {
    suspend fun create(usuarioId: UUID, mensaje: String, categoria: String): TicketConUsuario?
    suspend fun findByUsuarioId(usuarioId: UUID): List<TicketConUsuario>
}

class TicketRepositoryImpl(private val database: Database) : TicketRepository {

    /**
     * Crea un nuevo ticket de soporte
     */
    override suspend fun create(usuarioId: UUID, mensaje: String, categoria: String): TicketConUsuario? = db {
        val ticketId = UUID.randomUUID()

        TicketTable.insert {
            it[TicketTable.ticketId] = ticketId
            it[TicketTable.usuarioId] = usuarioId
            it[TicketTable.mensaje] = mensaje
            it[TicketTable.categoria] = categoria
            it[TicketTable.estado] = "abierto"
        }

        // Obtener el ticket creado con información del usuario
        (TicketTable innerJoin UsuarioTable)
            .selectAll()
            .where { TicketTable.ticketId eq ticketId }
            .limit(1)
            .firstOrNull()
            ?.let { row ->
                TicketConUsuario(
                    ticketId = row[TicketTable.ticketId],
                    usuarioId = row[TicketTable.usuarioId],
                    nombreUsuario = row[UsuarioTable.nombre],
                    correoUsuario = row[UsuarioTable.correo],
                    mensaje = row[TicketTable.mensaje],
                    categoria = row[TicketTable.categoria],
                    estado = row[TicketTable.estado],
                    fechaCreacion = row[TicketTable.fechaCreacion],
                    fechaActualizacion = row[TicketTable.fechaActualizacion]
                )
            }
    }

    /**
     * Obtiene todos los tickets de un usuario específico
     */
    override suspend fun findByUsuarioId(usuarioId: UUID): List<TicketConUsuario> = db {
        (TicketTable innerJoin UsuarioTable)
            .selectAll()
            .where { TicketTable.usuarioId eq usuarioId }
            .orderBy(TicketTable.fechaCreacion to SortOrder.DESC)
            .map { row ->
                TicketConUsuario(
                    ticketId = row[TicketTable.ticketId],
                    usuarioId = row[TicketTable.usuarioId],
                    nombreUsuario = row[UsuarioTable.nombre],
                    correoUsuario = row[UsuarioTable.correo],
                    mensaje = row[TicketTable.mensaje],
                    categoria = row[TicketTable.categoria],
                    estado = row[TicketTable.estado],
                    fechaCreacion = row[TicketTable.fechaCreacion],
                    fechaActualizacion = row[TicketTable.fechaActualizacion]
                )
            }
    }

    private suspend fun <T> db(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
}
