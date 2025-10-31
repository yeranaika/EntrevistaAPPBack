package data

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class UserRow(val id: UUID, val email: String, val hash: String, val nombre: String?)

private suspend inline fun <T> tx(noinline block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

class UserRepository {

    suspend fun existsByEmail(email: String): Boolean = tx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .any()
    }

    suspend fun create(email: String, hash: String, nombre: String?): UUID = tx {
        val id = UUID.randomUUID()
        UsuarioTable.insert {
            it[usuarioId] = id
            it[correo] = email
            it[contrasenaHash] = hash
            it[UsuarioTable.nombre] = nombre
        }
        id
    }

    suspend fun findByEmail(email: String): UserRow? = tx {
        UsuarioTable
            .selectAll()
            .where { UsuarioTable.correo eq email }
            .limit(1)
            .firstOrNull()
            ?.let { r ->
                UserRow(
                    id = r[UsuarioTable.usuarioId],
                    email = r[UsuarioTable.correo],
                    hash = r[UsuarioTable.contrasenaHash],
                    nombre = r[UsuarioTable.nombre]
                )
            }
    }
}
