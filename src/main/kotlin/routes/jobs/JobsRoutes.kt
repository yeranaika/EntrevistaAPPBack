package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import services.JSearchService

/**
 * Rutas para búsqueda de ofertas de empleo externas (JSearch).
 *
 * Ejemplo:
 * GET /jobs/search?q=enfermera chile&country=cl
 */
fun Route.jobsRoutes(jSearchService: JSearchService) {

    route("/jobs") {

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
    }
}
