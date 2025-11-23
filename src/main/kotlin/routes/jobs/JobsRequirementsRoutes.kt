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

// ---------- DTOs ----------

// JSON de entrada (Android / Postman)
@Serializable
data class JobRequirementsReq(
    val cargo: String,
    val area: String? = null,
    val country: String = "cl",
    val limitAvisos: Int = 5,
    val skillsLimit: Int = 10
)

@Serializable
data class JobRequirementItem(
    val fuenteTitulo: String,
    val empresa: String?,
    val ubicacion: String?,
    val nivelInferido: String,
    val requisitosTecnicos: List<String>,
    val requisitosBlandos: List<String>,
    val urlAviso: String?
)

@Serializable
data class JobRequirementsResponse(
    val cargo: String,
    val area: String?,
    val totalAvisosAnalizados: Int,
    val avisosUsados: Int,
    val maxRequisitosPorTipo: Int,
    val items: List<JobRequirementItem>
)

// ---------- helpers ESPECÍFICOS DE REQUIREMENTS ----------

fun inferNivelFromTitleReq(titulo: String): String {
    val t = titulo.lowercase()
    return when {
        "senior" in t || " sr" in t || "sr " in t -> "sr"
        "semi" in t || "medio" in t || "middle" in t || "ssr" in t -> "mid"
        "junior" in t || " jr" in t || "jr " in t || "trainee" in t -> "jr"
        else -> "jr"
    }
}

/**
 * Extrae skills técnicas y blandas desde el aviso.
 * (por ahora vacío hasta mapear los campos reales de JobNormalizedDto)
 */
fun extractRequirementsFromJob(job: JobNormalizedDto): Pair<List<String>, List<String>> {
    // TODO: mapear campos reales (job.descripcion, job.skills, etc.)
    return emptyList<String>() to emptyList<String>()
}

// ---------- RUTA SIMPLE (un solo cargo/meta) ----------

fun Route.jobsRequirementsRoutes(
    jSearchService: JSearchService,
    jobRequisitoRepository: JobRequisitoRepository
) {
    route("/jobs") {

        post("/requirements") {
            val req = try {
                call.receive<JobRequirementsReq>()
            } catch (e: Exception) {
                e.printStackTrace()
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "json_invalido", "message" to (e.message ?: "Body inválido"))
                )
            }

            val cargo = req.cargo
            val area = req.area
            val country = req.country
            val limitAvisos = req.limitAvisos
            val skillsLimit = req.skillsLimit

            val queryTexto = buildString {
                append(cargo.trim())
                if (!area.isNullOrBlank()) {
                    append(" ")
                    append(area.trim())
                }
            }

            try {
                val jobs: List<JobNormalizedDto> = jSearchService.searchJobs(
                    query = queryTexto,
                    country = country,
                    page = 1
                )

                val totalAvisosAnalizados = jobs.size
                val subset = jobs.take(limitAvisos)

                val rawItems = subset.map { job ->
                    val (requisitosTecnicos, requisitosBlandos) = extractRequirementsFromJob(job)

                    JobRequirementItem(
                        fuenteTitulo = job.titulo,
                        empresa = job.empresa,
                        ubicacion = null,
                        nivelInferido = inferNivelFromTitleReq(job.titulo),
                        requisitosTecnicos = requisitosTecnicos,
                        requisitosBlandos = requisitosBlandos,
                        urlAviso = null
                    )
                }

                val todosTecnicos = rawItems.flatMap { it.requisitosTecnicos }
                val todosBlandos = rawItems.flatMap { it.requisitosBlandos }

                val topTecnicos = todosTecnicos.distinct().take(skillsLimit)
                val topBlandos = todosBlandos.distinct().take(skillsLimit)

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

                jobRequisitoRepository.replaceRequirements(
                    cargo = cargo,
                    area = area,
                    items = finalItems
                )

                val res = JobRequirementsResponse(
                    cargo = cargo,
                    area = area,
                    totalAvisosAnalizados = totalAvisosAnalizados,
                    avisosUsados = subset.size,
                    maxRequisitosPorTipo = skillsLimit,
                    items = finalItems
                )

                call.respond(res)
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "jobs_requirements_error",
                        "message" to (e.message ?: "Error obteniendo requisitos del cargo")
                    )
                )
            }
        }
    }
}
