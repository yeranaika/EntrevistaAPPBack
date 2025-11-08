package data.repository.usuarios


import data.tables.usuarios.UsuarioTable
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
    val idioma: String?          // ← nullable para evitar NPE si la columna permite NULL o tiene default
)

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class EmailAlreadyInUseException(val email: String, cause: Throwable? = null) : RuntimeException(cause)

class UserRepository {

    /** ¿Existe un usuario con ese correo? */
    suspend fun existsByEmail(email: String): Boolean = dbTx {
        UsuarioTable
            .select(UsuarioTable.usuarioId)
            .where { UsuarioTable.correo eq email }
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
                if (idioma != null) it[UsuarioTable.idioma] = idioma   // si la columna es nullable o tiene default
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
            .selectAll()
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

    /** Actualiza solo el idioma. Retorna filas afectadas (0 o 1). */
    suspend fun updateIdioma(id: UUID, nuevoIdioma: String?): Int = dbTx {
        if (nuevoIdioma == null) return@dbTx 0
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[idioma] = nuevoIdioma
        }
    }

    /** Actualiza el hash de contraseña. Retorna filas afectadas (0 o 1). */
    suspend fun updatePasswordHash(id: UUID, newHash: String): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[contrasenaHash] = newHash
        }
    }

    /** Actualiza el hash de contraseña (alias usado por rutas). */
    suspend fun updatePassword(userId: UUID, newHash: String): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[contrasenaHash] = newHash
        }
    }

    // ---------- Mapper ----------
    private fun ResultRow.toUserRow() = UserRow(
        id     = this[UsuarioTable.usuarioId],
        email  = this[UsuarioTable.correo],
        hash   = this[UsuarioTable.contrasenaHash],
        nombre = this[UsuarioTable.nombre],
        idioma = this[UsuarioTable.idioma]    // compila tanto si la columna es nullable como si no
    )
}
