package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import services.JSearchService
import services.InterviewQuestionService
import services.JobNormalizedDto

// ===================== DTOs =====================

@Serializable
data class GenerateJobsRequest(
    val q: String,
    val country: String? = "cl",
    val n: Int = 3,
    val preguntasPorAviso: Int = 3
)

@Serializable
data class JobWithSavedQuestionsDto(
    val job: JobNormalizedDto,
    val preguntas: List<String>
)

@Serializable
data class GenerateJobsResponse(
    val message: String,
    val totalEmpleosProcesados: Int,
    val items: List<JobWithSavedQuestionsDto>
)

@Serializable
data class PreguntaDto(
    val preguntaId: String,
    val tipoBanco: String?,
    val sector: String?,
    val nivel: String?,
    val texto: String,
    val pistas: String?,     // lo mandamos como JSON string (si quieres luego lo parseas en el front)
    val activa: Boolean,
    val fechaCreacion: String
)

// ===================== Helpers =====================

/**
 * Determina el nivel (JR / SS / SR) en base al título del aviso.
 */
private fun inferNivelFromTitle(titulo: String): String {
    val t = titulo.lowercase()

    return when {
        "senior" in t || "sr " in t || " sr" in t -> "SR"
        "semi" in t || "semisenior" in t || "ssr" in t -> "SS"
        "junior" in t || " jr" in t || "jr " in t -> "JR"
        else -> "JR" // si no sabemos, asumimos junior
    }
}

// ===================== Rutas =====================

fun Route.jobsGeneratorRoutes(
    jSearchService: JSearchService,
    interviewQuestionService: InterviewQuestionService
) {

    route("/jobs") {

        /**
         * POST /jobs/generate-and-save
         *
         * Body:
         * {
         *   "q": "desarrollador backend",
         *   "country": "cl",
         *   "n": 2,
         *   "preguntasPorAviso": 3
         * }
         *
         * Flujo:
         *  - Busca avisos en la API de empleo (JSearch).
         *  - Genera preguntas con OpenAI por cada aviso.
         *  - Inserta cada pregunta en la tabla `pregunta`.
         *  - Devuelve resumen + detalle (job + preguntas).
         */
        post("/generate-and-save") {

            val req = try {
                call.receive<GenerateJobsRequest>()
            } catch (e: Exception) {
                e.printStackTrace()
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Body inválido",
                        "example" to """{ "q": "desarrollador backend", "country": "cl", "n": 2 }"""
                    )
                )
            }

            if (req.q.isBlank()) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "El campo q (query) es obligatorio")
                )
            }

            runCatching {
                val jobs = jSearchService.searchJobs(
                    query = req.q,
                    country = req.country,
                    page = 1
                )

                println("📌 Encontrados ${jobs.size} empleos para '${req.q}'")

                val subset = jobs.take(req.n)
                var totalGuardadas = 0

                val result = mutableListOf<JobWithSavedQuestionsDto>()

                for (job in subset) {
                    val preguntas = interviewQuestionService.generateQuestionsForJob(
                        job = job,
                        cantidad = req.preguntasPorAviso
                    )

                    println("\n🎯 Aviso: ${job.titulo} (${job.empresa ?: "Sin empresa"})")
                    preguntas.forEachIndexed { idx, p ->
                        println("   ❓ [${idx + 1}] $p")
                    }

                    // Inferimos nivel (JR / SS / SR) desde el título del aviso
                    val nivel = inferNivelFromTitle(job.titulo)

                    // Guardar en BD con SQL directo
                    transaction {
                        preguntas.forEach { textoPregunta ->
                            val safeTexto = textoPregunta.replace("'", "''")
                            val safeSector = (job.empresa ?: "GENERAL").replace("'", "''")
                            val safeTitulo = job.titulo.replace("'", "''")
                            val safeUbicacion = (job.ubicacion ?: "").replace("'", "''")
                            val safeFuente = job.fuente.replace("'", "''")
                            val safeIdExterno = job.idExterno.replace("'", "''")
                            val safeNivel = nivel.replace("'", "''")

                            val pistasJson = """
                                {
                                  "fuente": "$safeFuente",
                                  "idExterno": "$safeIdExterno",
                                  "tituloAviso": "$safeTitulo",
                                  "ubicacion": "$safeUbicacion"
                                }
                            """.trimIndent().replace("\n", "")

                            val newId = java.util.UUID.randomUUID()
                            val sql = """
                                INSERT INTO pregunta (pregunta_id, tipo_banco, sector, nivel, texto, pistas)
                                VALUES ('$newId', 'AUTO', '$safeSector', '$safeNivel', '$safeTexto', '$pistasJson'::json)
                            """.trimIndent()

                            TransactionManager.current().exec(sql)
                            println("💾 Insertada pregunta en BD: $textoPregunta (nivel=$nivel)")
                            totalGuardadas++
                        }
                    }

                    result += JobWithSavedQuestionsDto(
                        job = job,
                        preguntas = preguntas
                    )
                }

                GenerateJobsResponse(
                    message = "Se guardaron $totalGuardadas preguntas generadas automáticamente",
                    totalEmpleosProcesados = result.size,
                    items = result
                )
            }.onSuccess { payload ->
                call.respond(payload)
            }.onFailure { e ->
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf(
                        "error" to "server_error",
                        "message" to (e.message ?: "Error generando o guardando preguntas")
                    )
                )
            }
        }

        /**
         * GET /jobs/generated-questions
         *
         * Parámetros opcionales:
         *   - nivel: JR | SS | SR
         *   - sector: texto (busca exacto en columna sector)
         *   - limit: cantidad máxima de preguntas (por defecto 50)
         *
         * Ejemplos:
         *   /jobs/generated-questions
         *   /jobs/generated-questions?nivel=JR&limit=20
         *   /jobs/generated-questions?sector=Tata%20Consultancy%20Services%20Chile&nivel=SR
         */
        get("/generated-questions") {
            val nivelFilter = call.request.queryParameters["nivel"]  // JR / SS / SR
            val sectorFilter = call.request.queryParameters["sector"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val preguntas: List<PreguntaDto> = transaction {
                val conditions = mutableListOf<String>()

                // Solo tipo_banco = 'AUTO' (las generadas por IA)
                conditions += "tipo_banco = 'AUTO'"

                if (!nivelFilter.isNullOrBlank()) {
                    conditions += "nivel = '${nivelFilter.replace("'", "''")}'"
                }

                if (!sectorFilter.isNullOrBlank()) {
                    conditions += "sector = '${sectorFilter.replace("'", "''")}'"
                }

                val whereClause = if (conditions.isNotEmpty()) {
                    "WHERE " + conditions.joinToString(" AND ")
                } else {
                    ""
                }

                val sql = """
                    SELECT
                        pregunta_id,
                        tipo_banco,
                        sector,
                        nivel,
                        texto,
                        pistas,
                        activa,
                        fecha_creacion
                    FROM pregunta
                    $whereClause
                    ORDER BY fecha_creacion DESC
                    LIMIT $limit
                """.trimIndent()

                val list = mutableListOf<PreguntaDto>()

                TransactionManager.current().exec(sql) { rs ->
                    while (rs.next()) {
                        val id = rs.getObject("pregunta_id")?.toString() ?: ""
                        val tipoBanco = rs.getString("tipo_banco")
                        val sector = rs.getString("sector")
                        val nivel = rs.getString("nivel")
                        val texto = rs.getString("texto")
                        val pistas = rs.getString("pistas") // JSON como string
                        val activa = rs.getBoolean("activa")
                        val fechaCreacion = rs.getTimestamp("fecha_creacion")?.toInstant()?.toString() ?: ""

                        list += PreguntaDto(
                            preguntaId = id,
                            tipoBanco = tipoBanco,
                            sector = sector,
                            nivel = nivel,
                            texto = texto,
                            pistas = pistas,
                            activa = activa,
                            fechaCreacion = fechaCreacion
                        )
                    }
                }

                list
            }

            call.respond(preguntas)
        }
    }
}
