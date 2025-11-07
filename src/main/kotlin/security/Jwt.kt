package security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date

/**
 * Emite un Access Token JWT con claims mínimos.
 * @param subject userId (UUID en string)
 * @param extraClaims claims extra (ej. mapOf("role" to "admin"))
 * @param ttlSeconds duración del token (segundos)
 * Emite un Access Token JWT (corto).
 * @param subject userId (string UUID)
 * @param ttlSeconds duración en segundos (ej: 900 = 15min)
 */
fun issueAccessToken(
    subject: String,
    issuer: String,
    audience: String,
    algorithm: Algorithm,
    ttlSeconds: Int,
    extraClaims: Map<String, Any?> = emptyMap()
): String {
    val now = Instant.now()
    val exp = now.plusSeconds(ttlSeconds.toLong())

    val builder = JWT.create()
    ttlSeconds: Int
): String {
    val now = Instant.now()
    val exp = now.plusSeconds(ttlSeconds.toLong())
    return JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withSubject(subject)
        .withIssuedAt(Date.from(now))
        .withExpiresAt(Date.from(exp))

    // Adjunta claims extra (role, scopes, etc.)
    extraClaims.forEach { (k, v) ->
        when (v) {
            null -> {} // ignora nulos
            is String -> builder.withClaim(k, v)
            is Boolean -> builder.withClaim(k, v)
            is Int -> builder.withClaim(k, v)
            is Long -> builder.withClaim(k, v)
            is List<*> -> builder.withArrayClaim(k, v.filterIsInstance<String>().toTypedArray())
            else -> builder.withClaim(k, v.toString())
        }
    }

    return builder.sign(algorithm)
        .sign(algorithm)
}
