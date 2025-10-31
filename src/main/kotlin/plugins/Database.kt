package plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val s = settings()
    Database.connect(
        url = s.dbUrl,
        driver = "org.postgresql.Driver",
        user = s.dbUser,
        password = s.dbPass
    )
}
