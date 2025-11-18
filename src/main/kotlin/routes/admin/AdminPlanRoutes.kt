package routes.admin

import data.models.usuarios.PlanPracticaRes
import data.repository.AppAndroid.OnboardingRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import security.isAdmin  
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.adminPlanRoutes(
    onboardingRepo: OnboardingRepository
) {
    authenticate("auth-jwt") {
        route("/admin") {

            // POST /admin/usuarios/{usuarioId}/plan-practica
            post("/usuarios/{usuarioId}/plan-practica") {
                val principal = call.principal<JWTPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                // 🔁 antes mirabas "rol"; ahora usamos el helper isAdmin()
                if (!principal.isAdmin()) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "Solo admin puede usar esta ruta")
                    )
                }

                val usuarioIdStr = call.parameters["usuarioId"]
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Falta usuarioId en la URL")
                    )

                val usuarioId = try {
                    UUID.fromString(usuarioIdStr)
                } catch (e: IllegalArgumentException) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "usuarioId no es un UUID válido")
                    )
                }

                val body = call.receive<PlanPracticaRes>()

                val planGuardado = onboardingRepo.guardarPlanParaUsuario(
                    usuarioId = usuarioId,
                    req = body
                )

                call.respond(HttpStatusCode.Created, planGuardado)
            }
        }
    }
}
