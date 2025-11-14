package data.repository.billing

import data.tables.billing.SuscripcionTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.SortOrder
import java.time.Instant
import java.util.UUID

data class SuscripcionStatus(
    val isPremium: Boolean,
    val expiresAt: Long?
)

// Usamos la conexión global configurada en tu plugin de DB
class SuscripcionRepository {

    private suspend fun <T> tx(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction { block() }

    suspend fun upsertSubscription(
        usuarioId: UUID,
        plan: String,
        proveedor: String,
        estado: String,
        fechaExpiracionMillis: Long?
    ) = tx {
        val fechaExp: Instant? = fechaExpiracionMillis?.let { Instant.ofEpochMilli(it) }
        val ahora = Instant.now()

        val existente = SuscripcionTable
            .selectAll()
            .where { (SuscripcionTable.usuarioId eq usuarioId) and (SuscripcionTable.plan eq plan) }
            .limit(1)
            .singleOrNull()

        if (existente == null) {
            SuscripcionTable.insert {
                it[SuscripcionTable.usuarioId] = usuarioId
                it[SuscripcionTable.plan] = plan
                it[SuscripcionTable.proveedor] = proveedor
                it[SuscripcionTable.estado] = estado          // "activa", "inactiva", etc.
                it[SuscripcionTable.fechaInicio] = ahora
                it[SuscripcionTable.fechaRenovacion] = null
                it[SuscripcionTable.fechaExpiracion] = fechaExp
            }
        } else {
            SuscripcionTable.update(
                where = {
                    (SuscripcionTable.usuarioId eq usuarioId) and
                    (SuscripcionTable.plan eq plan)
                }
            ) {
                it[SuscripcionTable.estado] = estado
                it[SuscripcionTable.fechaExpiracion] = fechaExp
            }
        }
    }

    suspend fun getCurrentStatus(usuarioId: UUID): SuscripcionStatus = tx {
        val row = SuscripcionTable
            .selectAll()
            .where { SuscripcionTable.usuarioId eq usuarioId }
            .orderBy(SuscripcionTable.fechaInicio, SortOrder.DESC)
            .limit(1)
            .singleOrNull()

        if (row == null) {
            SuscripcionStatus(isPremium = false, expiresAt = null)
        } else {
            val estado = row[SuscripcionTable.estado]
            val expInstant: Instant? = row[SuscripcionTable.fechaExpiracion]
            val expMillis: Long? = expInstant?.toEpochMilli()
            val nowMillis = System.currentTimeMillis()

            // ⬅️ aquí el cambio importante:
            val isPremium = estado == "activa" && (expMillis == null || expMillis > nowMillis)

            SuscripcionStatus(isPremium = isPremium, expiresAt = expMillis)
        }
    }
}
