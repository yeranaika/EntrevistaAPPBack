package data.repository.usuarios


import data.tables.usuarios.ProfileTable
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

data class ProfileRow(
    val perfilId: UUID,
    val userId: UUID,
    val nivelExperiencia: String?,
    val area: String?,
    val pais: String?,
    val notaObjetivos: String?,
    val flagsAccesibilidad: JsonElement?
)

private suspend fun <T> dbTx(block: Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }

class ProfileRepository {

    suspend fun findByUser(userId: UUID): ProfileRow? = dbTx {
        ProfileTable
            .selectAll()
            .where { ProfileTable.usuarioId eq userId }
            .limit(1)
            .firstOrNull()
            ?.toRow()
    }

    suspend fun create(
        userId: UUID,
        nivelExperiencia: String? = null,
        area: String? = null,
        pais: String? = null,
        notaObjetivos: String? = null,
        flagsAccesibilidad: JsonElement? = null
    ): UUID = dbTx {
        val pid = UUID.randomUUID()
        ProfileTable.insert {
            it[perfilId]                 = pid
            it[usuarioId]                = userId
            it[ProfileTable.nivelExperiencia] = nivelExperiencia
            it[ProfileTable.area]        = area
            it[ProfileTable.pais]        = pais
            it[ProfileTable.notaObjetivos] = notaObjetivos
            it[ProfileTable.flagsAccesibilidad] = flagsAccesibilidad   // ← JsonElement directo
        }
        pid
    }

    suspend fun updatePartial(
        perfilId: UUID,
        nivelExperiencia: String? = null,
        area: String? = null,
        pais: String? = null,
        notaObjetivos: String? = null,
        flagsAccesibilidad: JsonElement? = null
    ): Int = dbTx {
        ProfileTable.update({ ProfileTable.perfilId eq perfilId }) {
            if (nivelExperiencia != null) it[ProfileTable.nivelExperiencia] = nivelExperiencia
            if (area != null)             it[ProfileTable.area]             = area
            if (pais != null)             it[ProfileTable.pais]             = pais
            if (notaObjetivos != null)    it[ProfileTable.notaObjetivos]    = notaObjetivos
            if (flagsAccesibilidad != null) it[ProfileTable.flagsAccesibilidad] = flagsAccesibilidad // ← JsonElement
        }
    }

    private fun ResultRow.toRow(): ProfileRow =
        ProfileRow(
            perfilId           = this[ProfileTable.perfilId],
            userId             = this[ProfileTable.usuarioId],
            nivelExperiencia   = this[ProfileTable.nivelExperiencia],
            area               = this[ProfileTable.area],
            pais               = this[ProfileTable.pais],
            notaObjetivos      = this[ProfileTable.notaObjetivos],
            flagsAccesibilidad = this[ProfileTable.flagsAccesibilidad]     // ← ya viene como JsonElement?
        )
}
