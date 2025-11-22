package routes.cuestionario

import data.repository.cuestionario.PlanPracticaRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.usuarios.ProfileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PlanPracticaRes(
    val id: String,
    val area: String?,
    val metaCargo: String?,
    val nivel: String?,
    val pasos: List<PasoRes>
)

@Serializable
data class PasoRes(
    val id: String,
    val orden: Int,
    val titulo: String,
    val descripcion: String?,
    val sesionesPorSemana: Int?
)

fun Route.planPracticaRoutes(
    planRepo: PlanPracticaRepository,
    profileRepo: ProfileRepository,
    objetivoRepo: ObjetivoCarreraRepository
) {
    authenticate("auth-jwt") {

        // SOLO consulta, no genera plan nuevo
        get("/plan-practica") {
            try {
                val uid = call.userIdFromJwt()
                call.application.environment.log.info("Fetching plan for user: $uid")

                val existingPlan = planRepo.findPlanByUser(uid)

                // ---> AQUÍ EL CAMBIO IMPORTANTE
                if (existingPlan == null) {
                    call.application.environment.log.info("No plan found for user: $uid")
                    return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf(
                            "error" to "PLAN_NOT_FOUND",
                            "message" to "No hay plan de práctica definido para este usuario."
                        )
                    )
                }
                // <--- FIN CAMBIO

                // Si sí hay plan, lo devolvemos normalmente
                val pasos = planRepo.findPasosByPlan(existingPlan.id)
                call.respond(
                    PlanPracticaRes(
                        id = existingPlan.id.toString(),
                        area = existingPlan.area,
                        metaCargo = existingPlan.metaCargo,
                        nivel = existingPlan.nivel,
                        pasos = pasos.map {
                            PasoRes(
                                id = it.id.toString(),
                                orden = it.orden,
                                titulo = it.titulo,
                                descripcion = it.descripcion,
                                sesionesPorSemana = it.sesionesPorSemana
                            )
                        }
                    )
                )

            } catch (e: Exception) {
                call.application.environment.log.error("Error fetching plan", e)
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

        // OPCIONAL: si quieres seguir teniendo la lógica de generación, la puedes colgar aquí:
        /*
        post("/plan-practica/generar") {
            val uid = call.userIdFromJwt()
            val perfil = profileRepo.findByUser(uid)
            val objetivo = objetivoRepo.findByUser(uid)

            val area = perfil?.area ?: "General"
            val nivel = perfil?.nivelExperiencia ?: "jr"
            val meta = objetivo?.nombreCargo ?: "Mejorar habilidades"

            val planId = planRepo.createPlan(uid, area, meta, nivel)
            val pasosGenerados = generarPasos(area, nivel, meta)

            pasosGenerados.forEachIndexed { index, paso ->
                planRepo.createPaso(
                    planId = planId,
                    orden = index + 1,
                    titulo = paso.titulo,
                    descripcion = paso.descripcion,
                    sesiones = paso.sesiones
                )
            }

            val pasosDb = planRepo.findPasosByPlan(planId)
            call.respond(
                PlanPracticaRes(
                    id = planId.toString(),
                    area = area,
                    metaCargo = meta,
                    nivel = nivel,
                    pasos = pasosDb.map {
                        PasoRes(
                            id = it.id.toString(),
                            orden = it.orden,
                            titulo = it.titulo,
                            descripcion = it.descripcion,
                            sesionesPorSemana = it.sesionesPorSemana
                        )
                    }
                )
            )
        }
        */
    }
}

// Helper para obtener ID del JWT
private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

// Lógica de generación de pasos (la dejo igual por si usas el POST /plan-practica/generar)
data class PasoTemplate(val titulo: String, val descripcion: String, val sesiones: Int)

fun generarPasos(area: String, nivel: String, meta: String): List<PasoTemplate> {
    val pasos = mutableListOf<PasoTemplate>()

    pasos.add(
        PasoTemplate(
            titulo = "Fundamentos de $area",
            descripcion = "Repaso de conceptos clave para $nivel en $area.",
            sesiones = 2
        )
    )

    pasos.add(
        PasoTemplate(
            titulo = "Práctica para $meta",
            descripcion = "Ejercicios enfocados en alcanzar el rol de $meta.",
            sesiones = 3
        )
    )

    pasos.add(
        PasoTemplate(
            titulo = "Simulación de Entrevista",
            descripcion = "Prueba técnica simulada nivel $nivel.",
            sesiones = 1
        )
    )

    if (nivel.contains("mid") || nivel.contains("sr") || nivel.contains("mucha")) {
        pasos.add(
            PasoTemplate(
                titulo = "Arquitectura y Diseño",
                descripcion = "Diseño de sistemas y patrones avanzados.",
                sesiones = 2
            )
        )
    }

    return pasos
}
