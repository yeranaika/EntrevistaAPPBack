package routes.auth

import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.routing.*

// Mantiene la ruta base /auth y monta las subrutas
fun Route.authRoutes(
    issuer: String,
    audience: String,
    algorithm: Algorithm
) = route("/auth") {
    registerRoutes(issuer, audience, algorithm)
    loginRoutes(issuer, audience, algorithm)
    resetRoutes()
    refreshRoutes(issuer, audience, algorithm)
}