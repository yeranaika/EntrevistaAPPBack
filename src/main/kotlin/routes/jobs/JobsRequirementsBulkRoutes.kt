package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import services.JSearchService
import services.JobNormalizedDto
import data.repository.jobs.JobRequisitoRepository

// Usa los DTOs ya definidos en JobsRequirementsRoutes.kt:
// JobRequirementsReq, JobRequirementItem, JobRequirementsResponse

@Serializable
data class BulkJobRequirementsReq(
    val items: List<JobRequirementsReq>
)

@Serializable
data class BulkJobRequirementsResponse(
    val results: List<JobRequirementsResponse>
)

fun Route.jobsRequirementsBulkRoutes(
    jSearchService: JSearchService,
    jobRequisitoRepository: JobRequisitoRepository
) {
    route("/jobs") {

        post("/requirements/bulk") {
            val body = try {
                call.receive<BulkJobRequirementsReq>()
            } catch (e: Exception) {
                e.printStackTrace()
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "json_invalido",
                        "message" to (e.message ?: "Body inválido")
                    )
                )
            }

            if (body.items.isEmpty()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "lista_vacia",
                        "message" to "Debes enviar al menos un cargo"
                    )
                )
            }

            val resultados = mutableListOf<JobRequirementsResponse>()

            for (item in body.items) {
                val cargo = item.cargo
                val area = item.area
                val country = item.country
                val limitAvisos = item.limitAvisos
                val skillsLimit = item.skillsLimit

                val queryTexto = buildString {
                    append(cargo.trim())
                    if (!area.isNullOrBlank()) {
                        append(" ")
                        append(area.trim())
                    }
                }

                // 1) Buscar avisos
                val jobs: List<JobNormalizedDto> = jSearchService.searchJobs(
                    query = queryTexto,
                    country = country,
                    page = 1
                )

                val totalAvisosAnalizados = jobs.size
                val subset = jobs.take(limitAvisos)

                // 2) Transformar avisos a JobRequirementItem "raw"
                val rawItems = subset.map { job ->
                    val (requisitosTecnicos, requisitosBlandos) = extractRequirementsFromJob(job)

                    JobRequirementItem(
                        fuenteTitulo = job.titulo,
                        empresa = job.empresa,
                        ubicacion = null,
                        // 👇 USAR EL HELPER NUEVO, NO el privado
                        nivelInferido = inferNivelFromTitleReq(job.titulo),
                        requisitosTecnicos = requisitosTecnicos,
                        requisitosBlandos = requisitosBlandos,
                        urlAviso = null
                    )
                }

                // 3) Consolidar requisitos
                val todosTecnicos = rawItems.flatMap { it.requisitosTecnicos }
                val todosBlandos  = rawItems.flatMap { it.requisitosBlandos }

                val topTecnicos = todosTecnicos.distinct().take(skillsLimit)
                val topBlandos  = todosBlandos.distinct().take(skillsLimit)

                val itemConsolidado = JobRequirementItem(
                    fuenteTitulo = "Requisitos consolidados para $cargo",
                    empresa = null,
                    ubicacion = null,
                    nivelInferido = "mix",
                    requisitosTecnicos = topTecnicos,
                    requisitosBlandos = topBlandos,
                    urlAviso = null
                )

                val finalItems = listOf(itemConsolidado)

                // 4) Guardar en BD
                jobRequisitoRepository.replaceRequirements(
                    cargo = cargo,
                    area = area,
                    items = finalItems
                )

                // 5) Armar response por cargo
                val res = JobRequirementsResponse(
                    cargo = cargo,
                    area = area,
                    totalAvisosAnalizados = totalAvisosAnalizados,
                    avisosUsados = subset.size,
                    maxRequisitosPorTipo = skillsLimit,
                    items = finalItems
                )

                resultados += res
            }

            call.respond(BulkJobRequirementsResponse(results = resultados))
        }
    }
}
