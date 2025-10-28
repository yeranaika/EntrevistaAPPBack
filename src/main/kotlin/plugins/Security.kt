package plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.AttributeKey

data class AuthCtx(val issuer: String, val audience: String, val algorithm: Algorithm)
val AuthCtxKey = AttributeKey<AuthCtx>("auth-ctx")

fun Application.configureSecurity() {
    val s = settings()
    val algorithm = Algorithm.HMAC512(s.jwtSecret)
    attributes.put(AuthCtxKey, AuthCtx(s.jwtIssuer, s.jwtAudience, algorithm))

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "app-entrevista"
            verifier(
                JWT.require(algorithm)
                    .withIssuer(s.jwtIssuer)
                    .withAudience(s.jwtAudience)
                    .build()
            )
            validate { cred -> if (cred.subject != null) JWTPrincipal(cred.payload) else null }
        }
    }
}
