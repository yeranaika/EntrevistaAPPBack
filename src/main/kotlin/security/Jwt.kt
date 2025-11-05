package security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date

/**
 * Emite un Access Token JWT (corto).
 * @param subject userId (string UUID)
 * @param ttlSeconds duración en segundos (ej: 900 = 15min)
 */
fun issueAccessToken(
    subject: String,
    issuer: String,
    audience: String,
    algorithm: Algorithm,
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
        .sign(algorithm)
}
