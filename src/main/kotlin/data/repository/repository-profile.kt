package data.repository

import data.tables.ProfileTable
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

private suspend fun <T> dbTx(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(context = Dispatchers.IO, statement = block)

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
            it[perfilId]                = pid
            it[usuarioId]               = userId
            it[ProfileTable.nivelExperiencia] = nivelExperiencia
            it[ProfileTable.area]              = area
            it[ProfileTable.pais]              = pais
            it[ProfileTable.notaObjetivos]     = notaObjetivos
            it[flagsAccTxt]             = flagsAccesibilidad?.toString() // guardamos JSON como String
        }
        pid
    }

    /** Update parcial por perfilId. */
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
            if (flagsAccesibilidad != null) it[flagsAccTxt]                 = flagsAccesibilidad.toString()
        }
    }

    // ---------- Mapper ----------
    private fun ResultRow.toRow(): ProfileRow {
        val raw = this[ProfileTable.flagsAccTxt]
        val json: JsonElement? = raw?.let {
            runCatching { Json.parseToJsonElement(it) }.getOrNull() ?: JsonNull
        }
        return ProfileRow(
            perfilId           = this[ProfileTable.perfilId],
            userId             = this[ProfileTable.usuarioId],
            nivelExperiencia   = this[ProfileTable.nivelExperiencia],
            area               = this[ProfileTable.area],
            pais               = this[ProfileTable.pais],
            notaObjetivos      = this[ProfileTable.notaObjetivos],
            flagsAccesibilidad = json
        )
    }
}
