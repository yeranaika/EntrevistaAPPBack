package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import services.JSearchService
import services.InterviewQuestionService
import services.JobNormalizedDto

@Serializable
data class JobWithQuestionsDto(
    val job: JobNormalizedDto,
    val preguntas: List<String>
)

/**
 * Rutas para búsqueda de trabajos (JSearch) y generación de preguntas (OpenAI).
 *
 * - GET /jobs/search?q=desarrollador chile&country=cl
 *   -> lista de JobNormalizedDto
 *
 * - GET /jobs/search-with-questions?q=desarrollador chile&country=cl&n=3
 *   -> lista de JobWithQuestionsDto (job + preguntas)
 */
fun Route.jobsRoutes(
    jSearchService: JSearchService,
    interviewQuestionService: InterviewQuestionService
) {

    route("/jobs") {

        // 1) Solo buscar trabajos
        get("/search") {
            val q = call.request.queryParameters["q"]
            if (q.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta parámetro q")
                )
            }

            val country = call.request.queryParameters["country"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1

            runCatching {
                jSearchService.searchJobs(
                    query = q,
                    country = country,
                    page = page
                )
            }.onSuccess { jobs ->
                // Debug opcional
                println("🔎 /jobs/search -> jobs size = ${jobs.size}")
                call.respond(jobs)
            }.onFailure { e ->
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "jsearch_error",
                        "message" to (e.message ?: "Error llamando a JSearch")
                    )
                )
            }
        }

        // 2) Buscar trabajos + generar preguntas con OpenAI
        get("/search-with-questions") {
            val q = call.request.queryParameters["q"]
            if (q.isNullOrBlank()) {
                return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Falta parámetro q")
                )
            }

            val country = call.request.queryParameters["country"]
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val cantidadPreguntas = call.request.queryParameters["n"]?.toIntOrNull() ?: 3

            runCatching {
                val jobs = jSearchService.searchJobs(
                    query = q,
                    country = country,
                    page = page
                )

                println("🧪 /jobs/search-with-questions -> jobs size = ${jobs.size}")

                // Para no fundir tokens, usamos solo los primeros 3 avisos
                val subset = jobs.take(3)

                val withQuestions = mutableListOf<JobWithQuestionsDto>()
                for (job in subset) {
                    val preguntas = interviewQuestionService
                        .generateQuestionsForJob(job, cantidadPreguntas)
                    withQuestions += JobWithQuestionsDto(
                        job = job,
                        preguntas = preguntas
                    )
                }

                withQuestions
            }.onSuccess { result ->
                call.respond(result)
            }.onFailure { e ->
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "jobs_with_questions_error",
                        "message" to (e.message ?: "Error generando preguntas")
                    )
                )
            }
        }
    }
}
