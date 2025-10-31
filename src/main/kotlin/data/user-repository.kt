package data

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class UserRow(
    val id: UUID,
    val email: String,
    val hash: String,
    val nombre: String?,
    val idioma: String
)

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class EmailAlreadyInUseException(val email: String, cause: Throwable? = null) : RuntimeException(cause)

class UserRepository {

    /** ¿Existe un usuario con ese correo? */
    suspend fun existsByEmail(email: String): Boolean = dbTx {
        UsuarioTable
            .select(UsuarioTable.usuarioId)                  // ✅ DSL nuevo
            .where { UsuarioTable.correo eq email }          // ✅ DSL nuevo
            .limit(1)
            .empty()
            .not()
    }

    /** Crea un usuario y devuelve su UUID. */
    suspend fun create(
        email: String,
        hash: String,
        nombre: String?,
        idioma: String?
    ): UUID = dbTx {
        val id = UUID.randomUUID()
        try {
            UsuarioTable.insert {
                it[usuarioId] = id
                it[correo] = email
                it[contrasenaHash] = hash
                it[UsuarioTable.nombre] = nombre
                if (idioma != null) it[UsuarioTable.idioma] = idioma
                // estado y fecha_creacion usan DEFAULT en DB
            }
            id
        } catch (e: ExposedSQLException) {
            if (e.sqlState == "23505") throw EmailAlreadyInUseException(email, e) // unique_violation
            throw e
        }
    }

    /** Busca por email. */
    suspend fun findByEmail(email: String): UserRow? = dbTx {
        UsuarioTable
            .selectAll()                                     // ✅ DSL nuevo
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .firstOrNull()
            ?.toUserRow()
    }

    /** Busca por ID. */
    suspend fun findById(id: UUID): UserRow? = dbTx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.usuarioId eq id }
            .limit(1)
            .firstOrNull()
            ?.toUserRow()
    }

    /** Actualiza solo el nombre. Retorna filas afectadas (0 o 1). */
    suspend fun updateNombre(id: UUID, nuevoNombre: String?): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[nombre] = nuevoNombre
        }
    }

    /** Actualiza el hash de contraseña. Retorna filas afectadas (0 o 1). */
    suspend fun updatePasswordHash(id: UUID, newHash: String): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[contrasenaHash] = newHash
        }
    }

    /** Elimina un usuario por ID. Retorna filas afectadas (0 o 1). */
    suspend fun deleteById(id: UUID): Int = dbTx {
        UsuarioTable.deleteWhere { usuarioId eq id }
    }

    // ---------- Helpers ----------
    private fun ResultRow.toUserRow() = UserRow(
        id     = this[UsuarioTable.usuarioId],
        email  = this[UsuarioTable.correo],
        hash   = this[UsuarioTable.contrasenaHash],
        nombre = this[UsuarioTable.nombre],
        idioma = this[UsuarioTable.idioma]
    )
}
