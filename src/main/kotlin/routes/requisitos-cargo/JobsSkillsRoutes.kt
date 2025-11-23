package routes.requisitos_cargo

import data.repository.requisitos_cargo.SkillsCargoRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Ruta HTTP que devuelve las habilidades de un cargo.
 * Se monta sobre /jobs/{cargo}/skills.
 */
fun Route.jobsSkillsRoutes(repository: SkillsCargoRepository) {
    route("/jobs") {
        get("{cargo}/skills") {
            val cargoParam = call.parameters["cargo"]
            if (cargoParam.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "El parámetro 'cargo' es obligatorio")
                return@get
            }
            val response = repository.getSkillsByCargo(cargoParam)
            call.respond(response)
        }
    }
}
