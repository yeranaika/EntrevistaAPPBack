package routes.billing

import data.models.billing.BillingStatusRes
import data.models.billing.VerifyPurchaseReq
import data.repository.billing.SuscripcionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import security.billing.GooglePlayBillingService
import java.util.UUID

fun Route.billingRoutes(
    billingService: GooglePlayBillingService,
    suscripcionRepo: SuscripcionRepository
) {

    // Las rutas de billing requieren que el usuario esté logueado (JWT)
    authenticate("auth-jwt") {

        route("/billing") {

            // La app Android manda el purchase_token y product_id
            post("/google/verify") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                // 👇 aquí obtienes el ID de usuario desde el JWT
                // asumiendo que viene en el "sub" como UUID
                val usuarioIdStr = principal.subject
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (_: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "userId inválido en token"
                    )
                }

                // Body: { "product_id": "...", "purchase_token": "...", "purchase_time": 123456 }
                val req = call.receive<VerifyPurchaseReq>()

                val result = billingService.verifyAndStore(req, usuarioId)

                if (result.success) {
                    call.respond(
                        HttpStatusCode.OK,
                        mapOf("status" to "premium_active")
                    )
                } else {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (result.errorMessage ?: "billing_failed"))
                    )
                }
            }

            // La app consulta si el usuario es premium o no
            get("/status") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val usuarioIdStr = principal.subject
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (_: IllegalArgumentException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        "userId inválido en token"
                    )
                }

                val status = suscripcionRepo.getCurrentStatus(usuarioId)

                call.respond(
                    BillingStatusRes(
                        isPremium = status.isPremium,
                        expiresAt = status.expiresAt
                    )
                )
            }
        }
    }
}
