package routes.admin

import data.repository.admin.InformeGestionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.informeGestionRoutes(
    informeRepo: InformeGestionRepository
) {
    authenticate("auth-jwt") {
        route("/admin/informes") {

            // GET /admin/informes/gestion
            get("/gestion") {
                // Solo verificamos que haya un JWT válido
                val principal = call.principal<JWTPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                // 🔴 YA NO HAY CHECK DE ROL AQUÍ
                // val rol = principal.payload.getClaim("rol").asString()
                // if (rol != "admin") { ... }

                val informe = informeRepo.obtenerInformeGestion()
                call.respond(HttpStatusCode.OK, informe)
            }

            // GET /admin/informes/gestion/excel
            get("/gestion/excel") {
                val informe = informeRepo.obtenerInformeGestion()
                val objetivosCarrera = informeRepo.obtenerObjetivosCarrera()
                val excelService = services.ExcelService()
                val fileBytes = excelService.generarExcelInforme(informe, objetivosCarrera)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "informe_gestion.xlsx").toString()
                )
                call.respondBytes(fileBytes, ContentType.Application.OctetStream)
            }

            // GET /admin/informes/gestion/csv
            get("/gestion/csv") {
                val informe = informeRepo.obtenerInformeGestion()
                val excelService = services.ExcelService()
                val csvContent = excelService.generarCsvInforme(informe)

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "informe_gestion.csv").toString()
                )
                call.respondText(csvContent, ContentType.Text.CSV)
            }
        }
    }
}
