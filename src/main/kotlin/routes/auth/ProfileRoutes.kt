package routes.auth

import data.models.usuarios.UpdateObjetivoReq
import data.models.usuarios.PlanResumenRes
import data.repository.AppAndroid.OnboardingRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.profileRoutes(
    onboardingRepo: OnboardingRepository
) {

    authenticate("auth-jwt") {

        // PUT /perfil/objetivo  → solo guarda área/meta/nivel (update)
        put("/perfil/objetivo") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.userIdFromJwt()
            val body = call.receive<UpdateObjetivoReq>()

            onboardingRepo.guardarObjetivo(
                usuarioId = userId,
                area = body.area,
                metaCargo = body.metaCargo,
                nivel = body.nivel
            )

            call.respond(HttpStatusCode.OK, mapOf("status" to "ok"))
        }

        // GET /plan-practica  → SOLO lo que hay en BD, sin generar nada
        get("/plan-practica") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.userIdFromJwt()

            val data = onboardingRepo.obtenerOnboarding(userId)
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta configurar área / meta / nivel para este usuario")
                )

            val res = PlanResumenRes(
                area = data.area,
                metaCargo = data.metaCargo,
                nivel = data.nivel
            )

            call.respond(HttpStatusCode.OK, res)
        }
    }
}

private fun JWTPrincipal.userIdFromJwt(): UUID {
    val sub = this.payload.getClaim("sub").asString()
    return UUID.fromString(sub)
}
