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
    val idioma: String?,
    val rol: String
)

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class EmailAlreadyInUseException(val email: String, cause: Throwable? = null) : RuntimeException(cause)

class UserRepository {

    /** ¿Existe un usuario con ese correo? */
    suspend fun existsByEmail(email: String): Boolean = dbTx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .any()
    }

    /** Crea un usuario y devuelve su UUID. */
    suspend fun create(
        email: String,
        hash: String,
        nombre: String?,
        idioma: String?,
        rol: String = "user"
    ): UUID = dbTx {
        val id = UUID.randomUUID()
        try {
            UsuarioTable.insert {
                it[usuarioId] = id
                it[correo] = email
                it[contrasenaHash] = hash
                it[UsuarioTable.nombre] = nombre
                if (idioma != null) it[UsuarioTable.idioma] = idioma
                it[UsuarioTable.rol] = rol
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

    /** Actualiza solo el nombre. */
    suspend fun updateNombre(id: UUID, nuevoNombre: String?): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[nombre] = nuevoNombre
        }
    }

    /** Actualiza solo el idioma. */
    suspend fun updateIdioma(id: UUID, nuevoIdioma: String?): Int = dbTx {
        if (nuevoIdioma == null) return@dbTx 0
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[idioma] = nuevoIdioma
        }
    }

    /** Actualiza el hash de contraseña. */
    suspend fun updatePasswordHash(id: UUID, newHash: String): Int = dbTx {
        UsuarioTable.update({ UsuarioTable.usuarioId eq id }) {
            it[contrasenaHash] = newHash
        }
    }

    /** Alias usado por rutas. */
    suspend fun updatePassword(userId: UUID, newHash: String): Int =
        updatePasswordHash(userId, newHash)

    /** Cambia el rol (user/admin). */
    suspend fun updateRol(userId: UUID, rol: String): Int = dbTx {
        require(rol == "user" || rol == "admin") { "rol inválido" }
        UsuarioTable.update({ UsuarioTable.usuarioId eq userId }) {
            it[UsuarioTable.rol] = rol
        }
    }

    /** ¿Es admin? (consulta directa en BD). */
    suspend fun isAdmin(userId: UUID): Boolean = dbTx {
        UsuarioTable
            .selectAll()
            .where { (UsuarioTable.usuarioId eq userId) and (UsuarioTable.rol eq "admin") }
            .limit(1)
            .any()
    }

    /** Elimina al usuario y devuelve true si existía. */
    suspend fun deleteById(userId: UUID): Boolean = dbTx {
        UsuarioTable.deleteWhere { UsuarioTable.usuarioId eq userId } > 0
    }

    // ---------- Mapper ----------
    private fun ResultRow.toUserRow() = UserRow(
        id     = this[UsuarioTable.usuarioId],
        email  = this[UsuarioTable.correo],
        hash   = this[UsuarioTable.contrasenaHash],
        nombre = this[UsuarioTable.nombre],
        idioma = this[UsuarioTable.idioma],
        rol    = this[UsuarioTable.rol]
    )
}
