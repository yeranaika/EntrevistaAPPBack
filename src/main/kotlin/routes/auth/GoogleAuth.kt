package routes.auth

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import data.repository.usuarios.UsuariosOAuthRepository
import security.auth.GoogleTokenVerifier
import security.tokens.TokenGoogleService
import security.AuthCtxKey   // ⬅
import java.util.UUID

// ⬇ FALTABA ESTE IMPORT
import io.ktor.server.auth.OAuthAccessTokenResponse

@Serializable
data class TokenPair(val access_token: String, val refresh_token: String)

fun Route.googleAuthRoutes(
    repo: UsuariosOAuthRepository,
    verifier: GoogleTokenVerifier
) {
    authenticate("google-oauth") {
        route("/auth/google") {
            get("/start") { /* redirige */ }

            get("/callback") {
                val log = call.application.log

                val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    ?: run {
                        log.error("OAuth principal NULL → fallo al canjear el 'code' con Google (revisa client_id/secret y redirect_uri)")
                        return@get call.respond(HttpStatusCode.Unauthorized, "No se pudo canjear el code con Google")
                    }

                // Útil para debug: ver si llegó 'id_token'
                log.debug("Google extraParameters names=" + principal.extraParameters.names().joinToString())

                val idToken = principal.extraParameters["id_token"]
                    ?: run {
                        log.error("Falta id_token → asegúrate de scope: openid email profile y que el client_secret es correcto")
                        return@get call.respond(HttpStatusCode.Unauthorized, "Falta id_token")
                    }

                val payload = verifier.verify(idToken)
                    ?: run {
                        log.error("ID token inválido → 'aud' no coincide con tu client_id o 'iss' inválido")
                        return@get call.respond(HttpStatusCode.Unauthorized, "ID token inválido")
                    }

                val sub = payload.subject
                val email = payload["email"] as? String
                val emailVerified = (payload["email_verified"] as? Boolean) ?: false
                if (!emailVerified || email.isNullOrBlank()) {
                    log.warn("Email no verificado o vacío. sub=$sub email=$email verified=$emailVerified")
                    return@get call.respond(HttpStatusCode.Unauthorized, "Email no verificado")
                }

                try {
                    val userId: UUID = repo.linkOrCreateFromGoogle(sub, email)

                    // ⬇ toma issuer/audience/algorithm desde el AuthCtx cargado en configureSecurity()
                    val ctx = call.application.attributes[AuthCtxKey]
                    val tokens = TokenGoogleService.issue(
                        userId = userId,
                        issuer = ctx.issuer,
                        audience = ctx.audience,
                        algorithm = ctx.algorithm
                    )

                    call.respond(tokens)
                } catch (e: Exception) {
                    log.error("Error procesando callback Google", e)
                    call.respond(HttpStatusCode.InternalServerError, "Error al completar el login")
                }
            }
        }
    }
}
