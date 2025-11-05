// plugins/Database.kt
package plugins

import data.tables.usuarios.UsuarioTable
import data.tables.usuarios.ConsentimientoTable
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureDatabase() {
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
            transaction {
                SchemaUtils.createMissingTablesAndColumns(
                    UsuarioTable,
                    ConsentimientoTable // incluye todas tus tablas aquí
                )
            }
            log.info("✅ DB conectada en intento ${attempt + 1}")
            return
        } catch (e: Throwable) {
            last = e
            log.warn("DB no lista aún (intento ${attempt + 1}): ${e.message}")
            // IMPORTANTE: usar sleep en vez de delay
            Thread.sleep(1_000) // 1 segundo
        }
    }
    error("No se pudo conectar a la DB: ${last?.message}")
}
