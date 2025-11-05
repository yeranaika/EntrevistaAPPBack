package data.repository

import data.tables.PasswordResetTable
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

class PasswordResetRepository(
    private val users: UserRepository = UserRepository()
) {
    data class ResetInfo(val token: UUID, val code: String, val userId: UUID)

    private val rng = SecureRandom()

    /** Crea un reset para un email existente; retorna token+code para DEV */
    suspend fun createForEmail(email: String): ResetInfo? = dbTx {
        val user = users.findByEmail(email.trim().lowercase()) ?: return@dbTx null

        val token = UUID.randomUUID()
        val code  = (100000 + rng.nextInt(900000)).toString() // 6 dígitos
        val now   = Instant.now()
        val exp   = now.plus(15, ChronoUnit.MINUTES)

        PasswordResetTable.insert {
            it[PasswordResetTable.token]     = token
            it[PasswordResetTable.usuarioId] = user.id
            it[PasswordResetTable.code]      = code
            it[PasswordResetTable.issuedAt]  = now
            it[PasswordResetTable.expiresAt] = exp
            it[PasswordResetTable.used]      = false
        }

        ResetInfo(token, code, user.id)
    }

    /**
     * Verifica y consume un reset válido (token+code, no usado, no expirado).
     * Devuelve el userId si se pudo consumir; null si no.
     */
    suspend fun consume(token: UUID, code: String): UUID? = dbTx {
        val row = PasswordResetTable
            .selectAll()
            .where {
                (PasswordResetTable.token eq token) and
                (PasswordResetTable.code eq code) and
                (PasswordResetTable.used eq false) and
                (PasswordResetTable.expiresAt greater Instant.now())
            }
            .limit(1)
            .firstOrNull()
            ?: return@dbTx null

        // Marcar como usado
        PasswordResetTable.update({ PasswordResetTable.token eq token }) {
            it[used] = true
        }

        row[PasswordResetTable.usuarioId]
    }
}
