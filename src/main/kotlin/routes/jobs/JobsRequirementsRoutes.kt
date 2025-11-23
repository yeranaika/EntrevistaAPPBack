package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import services.JSearchService
import services.JobNormalizedDto
import data.repository.jobs.JobRequisitoRepository   // repo para guardar en BD

// ---------- DTOs ----------

// JSON que envía Android / Postman
@Serializable
data class JobRequirementsReq(
    val cargo: String,
    val area: String? = null,
    val country: String = "cl",
    val limitAvisos: Int = 5,     // cuántos avisos máximo usar
    val skillsLimit: Int = 10     // cuántas skills máximo por tipo (técnicas / blandas)
)

@Serializable
data class JobRequirementItem(
    val fuenteTitulo: String,
    val empresa: String?,
    val ubicacion: String?,              // por ahora no mapeado
    val nivelInferido: String,
    val requisitosTecnicos: List<String>,
    val requisitosBlandos: List<String>,
    val urlAviso: String?                // por ahora no mapeado
)

@Serializable
data class JobRequirementsResponse(
    val cargo: String,
    val area: String?,
    val totalAvisosAnalizados: Int,      // cuántos avisos devolvió JSearch
    val avisosUsados: Int,               // cuántos se usaron (limitAvisos)
    val maxRequisitosPorTipo: Int,       // ej: 10 (técnicos) + 10 (blandos)
    val items: List<JobRequirementItem>  // normalmente 1 item consolidado
)

// ---------- Helpers ----------

/**
 * Determina el nivel (jr / mid / sr) en base al título del aviso.
 */
private fun inferNivelFromTitle(titulo: String): String {
    val t = titulo.lowercase()

    return when {
        "senior" in t || " sr" in t || "sr " in t -> "sr"
        "semi" in t || "medio" in t || "middle" in t || "ssr" in t -> "mid"
        "junior" in t || " jr" in t || "jr " in t || "trainee" in t -> "jr"
        else -> "jr"
    }
}

/**
 * Extrae requisitos técnicos y blandos de un JobNormalizedDto
 * usando su representación en texto (job.toString()).
 */
private fun extractRequirementsFromJob(job: JobNormalizedDto): Pair<List<String>, List<String>> {
    val text = job.toString()
    val lower = text.lowercase()

    // Palabras clave técnicas (puedes ir agregando más)
    val technicalKeywords = listOf(
        "kotlin", "java", "android", "compose", "jetpack",
        "sql", "postgres", "mysql", "rest", "api", "apis",
        "spring", "hibernate", "docker", "kubernetes", "aws", "azure", "gcp",
        "git", "github", "gitlab", "ci/cd", "ci cd",
        "testing", "junit", "mockito", "unit test", "integración",
        "javascript", "typescript", "react", "node", "angular",
        "microservicio", "microservicios"
    )

    // Palabras clave blandas
    val softKeywords = listOf(
        "comunicación", "comunicacion", "trabajo en equipo", "colaboración",
        "colaboracion", "liderazgo", "proactivo", "proactiva", "proactividad",
        "autonomía", "autonomia", "resolución de problemas", "resolucion de problemas",
        "adaptabilidad", "flexibilidad", "empatía", "empatia", "organización",
        "organizacion", "gestión del tiempo", "gestion del tiempo",
        "aprendizaje continuo", "responsabilidad"
    )

    // 1) Detectar keywords que aparecen en el texto
    val detectedTech = technicalKeywords
        .filter { it in lower }
        .map { kw -> kw.replaceFirstChar { c -> c.uppercaseChar() } }

    val detectedSoft = softKeywords
        .filter { it in lower }
        .map { kw ->
            // Capitalizar primera letra de cada palabra
            kw.split(" ").joinToString(" ") { w ->
                w.replaceFirstChar { c -> c.uppercaseChar() }
            }
        }

    // 2) Además, intentar cortar el texto en frases "tipo bullet"
    val rawSegments = text
        .split('\n', '.', ';', '•', '-', '·', '*', '●')
        .map { it.trim() }
        .filter { it.length in 10..180 } // descartar cosas muy cortas o larguísimas

    val extraTech = mutableListOf<String>()
    val extraSoft = mutableListOf<String>()

    for (seg in rawSegments) {
        val segLower = seg.lowercase()
        val isTechSeg = technicalKeywords.any { it in segLower }
        val isSoftSeg = softKeywords.any { it in segLower }

        if (isTechSeg) extraTech += seg
        if (isSoftSeg) extraSoft += seg
    }

    val allTech = (detectedTech + extraTech).distinct()
    val allSoft = (detectedSoft + extraSoft).distinct()

    return allTech to allSoft
}

// ---------- Rutas ----------

/**
 * Rutas para obtener requisitos de un cargo a partir del onboarding.
 *
 * POST /jobs/requirements
 *
 * Body JSON de ejemplo:
 * {
 *   "cargo": "Desarrollador Android",
 *   "area": "Desarrollador",
 *   "country": "cl",
 *   "limitAvisos": 5,
 *   "skillsLimit": 10
 * }
 */
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

            // Texto que vamos a mandar a JSearch: cargo + área
            val queryTexto = buildString {
                append(cargo.trim())
                if (!area.isNullOrBlank()) {
                    append(" ")
                    append(area.trim())
                }
            }

            runCatching {
                // 1) Buscar avisos en JSearch
                val jobs: List<JobNormalizedDto> = jSearchService.searchJobs(
                    query = queryTexto,
                    country = country,
                    page = 1
                )

                val totalAvisosAnalizados = jobs.size

                // Solo usamos los primeros N avisos según parámetro
                val subset = jobs.take(limitAvisos)

                // 2) Transformar cada aviso a JobRequirementItem "raw"
                val rawItems = subset.map { job ->
                    val (requisitosTecnicos, requisitosBlandos) = extractRequirementsFromJob(job)

                    JobRequirementItem(
                        fuenteTitulo = job.titulo,      // ej: "Android Developer Semi Senior"
                        empresa = job.empresa,          // si existe en JobNormalizedDto
                        ubicacion = null,               // ej: job.ubicacion si luego lo mapeas
                        nivelInferido = inferNivelFromTitle(job.titulo),
                        requisitosTecnicos = requisitosTecnicos,
                        requisitosBlandos = requisitosBlandos,
                        urlAviso = null                 // ej: job.url si luego lo mapeas
                    )
                }

                // 3) Consolidar requisitos de TODOS los avisos:
                //    - juntar todos
                //    - eliminar duplicados
                //    - quedarse con máximo 'skillsLimit' por tipo
                val todosTecnicos = rawItems.flatMap { it.requisitosTecnicos }
                val todosBlandos  = rawItems.flatMap { it.requisitosBlandos }

                val topTecnicos = todosTecnicos.distinct().take(skillsLimit)
                val topBlandos  = todosBlandos.distinct().take(skillsLimit)

                // 4) Crear un solo item "consolidado" con esos requisitos filtrados
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

                // 5) Guardar solo estos requisitos consolidados en la tabla job_requisito
                jobRequisitoRepository.replaceRequirements(
                    cargo = cargo,
                    area = area,
                    items = finalItems
                )

                // 6) Construir respuesta al cliente (Android / Postman)
                JobRequirementsResponse(
                    cargo = cargo,
                    area = area,
                    totalAvisosAnalizados = totalAvisosAnalizados,
                    avisosUsados = subset.size,
                    maxRequisitosPorTipo = skillsLimit,
                    items = finalItems
                )
            }.onSuccess { res ->
                call.respond(res)
            }.onFailure { e ->
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
