// src/main/kotlin/plugins/Settings.kt
package plugins

import io.ktor.server.application.*
import io.github.cdimascio.dotenv.dotenv

data class Settings(
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtSecret: String
)

fun Application.settings(): Settings {
    val cfg = environment.config
    val env = dotenv {
        ignoreIfMissing = true   // en CI/Prod puedes no tener .env
    }

    fun read(key: String, path: String): String =
        env[key]                                   // 1) .env
        ?: System.getenv(key)                      // 2) variables de entorno del SO
        ?: cfg.propertyOrNull(path)?.getString()   // 3) application.yaml
        ?: error("Falta config: $key / $path")

    return Settings(
        dbUrl       = read("DB_URL", "db.url"),
        dbUser      = read("DB_USER", "db.user"),
        dbPass      = read("DB_PASS", "db.pass"),
        jwtIssuer   = read("JWT_ISSUER", "security.jwt.issuer"),
        jwtAudience = read("JWT_AUDIENCE", "security.jwt.audience"),
        jwtSecret   = read("JWT_SECRET", "security.jwt.secret"),
    )
}