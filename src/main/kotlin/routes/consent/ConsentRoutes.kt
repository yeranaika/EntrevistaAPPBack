// routes/consent/ConsentRoutes.kt
package routes.consent

import data.mapper.toConsentRes
import data.repository.usuarios.ConsentimientoRepository
import data.repository.ConsentimientoRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.ConsentRoutes(consentRepo: ConsentimientoRepository) {

    authenticate("auth-jwt") {
        route("/me/consent") {   // ← barra inicial

            post {
                val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val body = runCatching { call.receive<CreateConsentReq>() }
                    .getOrElse { return@post call.respond(HttpStatusCode.BadRequest, "JSON inválido") }

                if (body.version.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, "version requerida")
                if (body.alcances.isEmpty()) return@post call.respond(HttpStatusCode.BadRequest, "alcances requerido")

                val row = consentRepo.create(userId, body.version, body.alcances)
                call.respond(HttpStatusCode.Created, row.toConsentRes())
            }

            get("/latest") {
                val principal = call.principal<JWTPrincipal>() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull() ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val row = consentRepo.latestVigente(userId)
                if (row == null) call.respond(HttpStatusCode.NoContent) else call.respond(row.toConsentRes())
            }

            post("/revoke") {
                val principal = call.principal<JWTPrincipal>() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val userId = principal.userIdOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val updated = consentRepo.revokeLatest(userId)
                if (updated == 0) call.respond(HttpStatusCode.NotFound, "No hay consentimiento vigente")
                else call.respond(RevokeRes(true))
            }
        }
    }
}

// ---- Helper SIMPLE: usa el subject (sub) del JWT ----
private fun JWTPrincipal.userIdOrNull(): UUID? =
    runCatching { UUID.fromString(this.subject) }.getOrNull()
