package routes.cuestionario

import data.repository.cuestionario.PlanPracticaRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.usuarios.ProfileRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
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
    objetivoRepo: ObjetivoCarreraRepository,
    testRepo: data.repository.nivelacion.TestNivelacionRepository
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

        // POST /plan-practica/generar-desde-test
        // Genera un plan de práctica basado en el resultado de un test de nivelación
        post("/plan-practica/generar-desde-test") {
            try {
                val uid = call.userIdFromJwt()

                @Serializable
                data class GenerarPlanReq(val testNivelacionId: String)

                val request = call.receive<GenerarPlanReq>()
                val testId = java.util.UUID.fromString(request.testNivelacionId)

                // Obtener resultado del test
                val test = testRepo.findById(testId)
                    ?: return@post call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Test de nivelación no encontrado")
                    )

                // Verificar que el test pertenezca al usuario
                if (test.usuarioId != uid) {
                    return@post call.respond(
                        HttpStatusCode.Forbidden,
                        mapOf("error" to "No tienes permiso para acceder a este test")
                    )
                }

                // Obtener objetivo del usuario
                val objetivo = objetivoRepo.findByUser(uid)
                val nombreCargo = objetivo?.nombreCargo ?: "Profesional en ${test.area}"

                // Determinar nivel del plan basado en el nivel del test
                val nivelPlan = test.nivel ?: "jr"

                // Desactivar planes anteriores del usuario
                val existingPlan = planRepo.findPlanByUser(uid)
                // (El repositorio ya maneja la desactivación en createPlan)

                // Crear nuevo plan
                val planId = planRepo.createPlan(
                    userId = uid,
                    area = test.area,
                    metaCargo = nombreCargo,
                    nivel = nivelPlan
                )

                // Generar pasos según el nivel detectado en el test
                val pasosGenerados = generarPasosPorNivel(
                    area = test.area ?: "General",
                    nivel = nivelPlan,
                    cargo = nombreCargo
                )

                // Insertar pasos en la base de datos
                pasosGenerados.forEachIndexed { index, paso ->
                    planRepo.createPaso(
                        planId = planId,
                        orden = index + 1,
                        titulo = paso.titulo,
                        descripcion = paso.descripcion,
                        sesiones = paso.sesionesPorSemana
                    )
                }

                // Obtener el plan completo con pasos
                val pasosDb = planRepo.findPasosByPlan(planId)

                call.respond(
                    HttpStatusCode.Created,
                    PlanPracticaRes(
                        id = planId.toString(),
                        area = test.area,
                        metaCargo = nombreCargo,
                        nivel = nivelPlan,
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

            } catch (e: IllegalArgumentException) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "ID de test inválido: ${e.message}")
                )
            } catch (e: Exception) {
                call.application.environment.log.error("Error generando plan desde test", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Error generando plan: ${e.message}")
                )
            }
        }
    }
}

// Helper para obtener ID del JWT
private fun ApplicationCall.userIdFromJwt(): UUID {
    val principal = this.principal<JWTPrincipal>() ?: error("No principal")
    val sub = principal.subject ?: error("No subject")
    return UUID.fromString(sub)
}

// Lógica de generación de pasos mejorada por nivel
data class PasoTemplate(val titulo: String, val descripcion: String, val sesionesPorSemana: Int)

/**
 * Genera pasos de plan de práctica según el nivel detectado en el test de nivelación
 */
fun generarPasosPorNivel(area: String, nivel: String, cargo: String): List<PasoTemplate> {
    val pasos = mutableListOf<PasoTemplate>()

    when (nivel.lowercase()) {
        "jr", "junior", "básico" -> {
            // Plan para nivel BÁSICO (4-5 pasos)
            pasos.add(PasoTemplate(
                titulo = "Fundamentos de $area",
                descripcion = "Repaso y consolidación de conceptos básicos esenciales en $area. Incluye teoría y ejercicios prácticos.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Práctica Guiada",
                descripcion = "Ejercicios paso a paso con retroalimentación inmediata para reforzar el aprendizaje.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Proyecto Simple",
                descripcion = "Desarrollo de un proyecto básico aplicando los conceptos aprendidos.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Preparación para Entrevistas",
                descripcion = "Simulación de entrevistas técnicas nivel junior para el rol de $cargo.",
                sesionesPorSemana = 1
            ))
        }

        "mid", "ssr", "semi senior", "intermedio" -> {
            // Plan para nivel INTERMEDIO (5-6 pasos)
            pasos.add(PasoTemplate(
                titulo = "Fundamentos Avanzados de $area",
                descripcion = "Profundización en conceptos intermedios y mejores prácticas en $area.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Frameworks y Herramientas",
                descripcion = "Dominio de frameworks modernos y herramientas profesionales del ecosistema.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Proyecto de Complejidad Moderada",
                descripcion = "Desarrollo de proyecto con arquitectura escalable y buenas prácticas.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Patrones de Diseño",
                descripcion = "Aplicación práctica de patrones de diseño comunes en la industria.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Preparación para Entrevistas Semi-Senior",
                descripcion = "Simulación de entrevistas técnicas nivel semi-senior para $cargo, incluyendo casos de estudio.",
                sesionesPorSemana = 1
            ))
        }

        "sr", "senior", "avanzado" -> {
            // Plan para nivel AVANZADO (6-7 pasos)
            pasos.add(PasoTemplate(
                titulo = "Arquitectura de Sistemas",
                descripcion = "Diseño de arquitecturas escalables, resilientes y de alto rendimiento en $area.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Optimización y Performance",
                descripcion = "Técnicas avanzadas de optimización, profiling y mejora de rendimiento.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Proyecto Complejo",
                descripcion = "Desarrollo de sistema completo con microservicios, escalabilidad y monitoreo.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Liderazgo Técnico",
                descripcion = "Mentoría, code reviews, decisiones arquitecturales y gestión técnica de equipos.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Preparación para Entrevistas Senior",
                descripcion = "Simulación de entrevistas de arquitectura y liderazgo técnico para $cargo.",
                sesionesPorSemana = 1
            ))

            pasos.add(PasoTemplate(
                titulo = "Contribución Open Source (Opcional)",
                descripcion = "Participación en proyectos open source relevantes para fortalecer perfil profesional.",
                sesionesPorSemana = 1
            ))
        }

        else -> {
            // Plan por defecto (similar a básico)
            pasos.add(PasoTemplate(
                titulo = "Fundamentos de $area",
                descripcion = "Repaso de conceptos clave en $area.",
                sesionesPorSemana = 2
            ))

            pasos.add(PasoTemplate(
                titulo = "Práctica para $cargo",
                descripcion = "Ejercicios enfocados en alcanzar el rol de $cargo.",
                sesionesPorSemana = 3
            ))

            pasos.add(PasoTemplate(
                titulo = "Simulación de Entrevista",
                descripcion = "Prueba técnica simulada.",
                sesionesPorSemana = 1
            ))
        }
    }

    return pasos
}
