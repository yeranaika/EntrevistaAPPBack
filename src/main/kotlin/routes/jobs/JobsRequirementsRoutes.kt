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
 * Extrae skills técnicas y blandas desde el aviso usando los
 * campos REALES de JobNormalizedDto:
 *  - descripcion
 *  - responsabilidades
 *  - requisitos
 *  - habilidades
 */
fun extractRequirementsFromJob(job: JobNormalizedDto): Pair<List<String>, List<String>> {
    val tecnicos = mutableListOf<String>()
    val blandos = mutableListOf<String>()

    // 1) Lo que ya viene separado desde JSearch:
    //    - habilidades  -> casi siempre técnicas
    //    - requisitos   -> también suelen ser técnicos
    tecnicos += job.habilidades
    tecnicos += job.requisitos

    // 2) Responsabilidades: aquí mezclamos, separamos por keywords
    job.responsabilidades.forEach { linea ->
        val l = linea.lowercase()

        val esTecnica = listOf(
            "java", "kotlin", "android", "spring", "rest", "api",
            "microservices", "microservicios", "sql", "mysql", "postgres",
            "docker", "kubernetes", "aws", "azure", "gcp", "linux",
            "javascript", "react", "node", "typescript", "python",
            "devops", "ci/cd", "pipelines"
        ).any { it in l }

        val esBlanda = listOf(
            "comunicación", "comunicacion", "trabajo en equipo",
            "liderazgo", "colaboración", "colaboracion",
            "orientación al detalle", "orientacion al detalle",
            "proactivo", "proactiva", "autonomía", "autonomia",
            "resolución de problemas", "resolucion de problemas"
        ).any { it in l }

        when {
            esTecnica -> tecnicos += linea
            esBlanda  -> blandos += linea
            // si no matchea ninguna, la ignoramos
        }
    }

    // 3) Extra heurística desde la descripción larga
    job.descripcion
        .split("\n", ".", "•", "-", "–")
        .map { it.trim() }
        .filter { it.length > 10 }
        .forEach { frase ->
            val f = frase.lowercase()

            val esTecnica = listOf(
                "java", "kotlin", "android", "spring", "rest", "api",
                "microservices", "microservicios", "sql", "database",
                "mysql", "postgres", "docker", "kubernetes", "aws",
                "azure", "gcp", "linux", "bash", "shell", "git",
                "testing", "automation", "selenium", "cypress"
            ).any { it in f }

            val esBlanda = listOf(
                "comunicación", "comunicacion", "trabajo en equipo",
                "liderazgo", "colaboración", "colaboracion",
                "orientación al detalle", "orientacion al detalle",
                "resolución de problemas", "resolucion de problemas",
                "proactivo", "proactiva", "autonomía", "autonomia"
            ).any { it in f }

            when {
                esTecnica -> tecnicos += frase
                esBlanda  -> blandos += frase
            }
        }

    // 4) Normalizar (limpiar y quitar duplicados)
    val tecnicosFinal = tecnicos
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    val blandosFinal = blandos
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

    return tecnicosFinal to blandosFinal
}

/**
 * LÓGICA COMPARTIDA: procesa un solo JobRequirementsReq.
 * La usan tanto /jobs/requirements como /jobs/requirements/bulk.
 */
suspend fun processJobRequirementsItem(
    req: JobRequirementsReq,
    jSearchService: JSearchService,
    jobRequisitoRepository: JobRequisitoRepository
): JobRequirementsResponse {

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
            ubicacion = job.ubicacion,
            nivelInferido = inferNivelFromTitleReq(job.titulo),
            requisitosTecnicos = requisitosTecnicos,
            requisitosBlandos = requisitosBlandos,
            urlAviso = null   // si luego agregas campo url en JobNormalizedDto, lo mapeas aquí
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

    // guarda en la tabla job_requisito
    jobRequisitoRepository.replaceRequirements(
        cargo = cargo,
        area = area,
        items = finalItems
    )

    return JobRequirementsResponse(
        cargo = cargo,
        area = area,
        totalAvisosAnalizados = totalAvisosAnalizados,
        avisosUsados = subset.size,
        maxRequisitosPorTipo = skillsLimit,
        items = finalItems
    )
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

            runCatching {
                processJobRequirementsItem(req, jSearchService, jobRequisitoRepository)
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