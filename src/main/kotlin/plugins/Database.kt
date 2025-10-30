// plugins/Database.kt
package plugins

import data.UsuarioTable
import io.ktor.server.application.*
import kotlinx.coroutines.delay
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.seconds

suspend fun Application.configureDatabase() {
    val s = settings()
    var last: Throwable? = null
    repeat(30) { attempt ->
        try {
            Database.connect(
                url = s.dbUrl,
                driver = "org.postgresql.Driver",
                user = s.dbUser,
                password = s.dbPass
            )
            // ping + crea tabla si falta
            transaction { SchemaUtils.createMissingTablesAndColumns(UsuarioTable) }
            log.info("✅ DB conectada en intento ${attempt + 1}")
            return
        } catch (e: Throwable) {
            last = e
            log.warn("DB no lista aún (intento ${attempt + 1}): ${e.message}")
            delay(1.seconds)
        }
    }
    error("No se pudo conectar a la DB: ${last?.message}")
}
