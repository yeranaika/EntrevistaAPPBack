package plugins

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*

data class Settings(
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtSecret: String
)

fun Application.settings(): Settings {
    // Ktor ya carga application.yaml; si pasas -Dconfig.file o -Dconfig.resource, se fusiona/override.
    val cfg = environment.config
    fun get(path: String): String =
        cfg.propertyOrNull(path)?.getString() ?: error("Falta config: $path")

    return Settings(
        dbUrl       = get("db.url"),
        dbUser      = get("db.user"),
        dbPass      = get("db.pass"),
        jwtIssuer   = get("security.jwt.issuer"),
        jwtAudience = get("security.jwt.audience"),
        jwtSecret   = get("security.jwt.secret"),
    )
}
