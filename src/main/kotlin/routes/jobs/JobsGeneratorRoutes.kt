package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import services.JSearchService
import services.InterviewQuestionService
import services.JobNormalizedDto
import services.MixedGeneratedQuestionDto

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
    val preguntas: List<String> // solo devolvemos los enunciados al cliente
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
    val pistas: String?,           // JSON como string
    val configRespuesta: String?,  // JSON como string
    val activa: Boolean,
    val fechaCreacion: String
)

// ===================== Helpers =====================

/**
 * Determina el nivel (jr / medio / sr) en base al título del aviso.
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
 * Convierte lista de pistas (List<String>) a JSON string seguro para insertar.
 * Ej: ["a", "b"] -> ["a", "b"]
 */
private fun buildPistasJson(pistas: List<String>): String {
    if (pistas.isEmpty()) return "[]"
    val safeHints = pistas.map { it.replace("\"", "\\\"") }
    val joined = safeHints.joinToString(
        separator = "\", \"",
        prefix = "[\"",
        postfix = "\"]"
    )
    return joined
}

/**
 * Construye el JSON de config_respuesta para una pregunta mixta.
 */
private fun buildConfigRespuestaJson(q: MixedGeneratedQuestionDto): String {
    return if (q.tipo == "seleccion_unica") {
        // Cerrada con alternativas
        val safeOpciones = q.opciones.map { opt ->
            val safeTexto = opt.texto.replace("\"", "\\\"")
            """{"id":"${opt.id}","texto":"$safeTexto"}"""
        }
        val opcionesJoined = safeOpciones.joinToString(separator = ",", prefix = "[", postfix = "]")
        val correct = q.respuesta_correcta ?: ""

        """
        {
          "tipo": "seleccion_unica",
          "opciones": $opcionesJoined,
          "respuesta_correcta": "$correct"
        }
        """.trimIndent().replace("\n", "")
    } else {
        // Abierta
        val min = q.min_caracteres ?: 20
        val max = q.max_caracteres ?: 300

        """
        {
          "tipo": "abierta_texto",
          "min_caracteres": $min,
          "max_caracteres": $max
        }
        """.trimIndent().replace("\n", "")
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
         *  - Genera preguntas MIXTAS (cerradas + abiertas) con OpenAI por cada aviso.
         *  - Inserta cada pregunta en la tabla `pregunta`.
         *  - Devuelve resumen + detalle (job + enunciados de preguntas).
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
                    // NUEVO: preguntas mixtas (cerradas + abiertas)
                    val preguntasGeneradas = interviewQuestionService.generateMixedQuestionsForJob(
                        job = job,
                        cantidad = req.preguntasPorAviso
                    )

                    println("\n🎯 Aviso: ${job.titulo} (${job.empresa ?: "Sin empresa"})")
                    preguntasGeneradas.forEachIndexed { idx, p ->
                        println("   ❓ [${idx + 1}] (${p.tipo}) ${p.enunciado}")
                    }

                    // Inferimos nivel (jr / medio / sr) desde el título del aviso
                    val nivel = inferNivelFromTitle(job.titulo)

                    transaction {
                        preguntasGeneradas.forEach { q ->
                            val safeTexto = q.enunciado.replace("'", "''")
                            // sector = título del aviso (ej: "Desarrollador Backend")
                            val safeSector = job.titulo.replace("'", "''")
                            val rawNivel = q.nivel ?: nivel
                            val safeNivel = when (rawNivel.lowercase()) {
                                "medio", "intermedio", "semi", "ssr", "middle" -> "mid"
                                "senior", "sr" -> "sr"
                                else -> "jr"
                            }.replace("'", "''")

                            // pistas: array de strings
                            val pistasJson = buildPistasJson(q.pistas)
                            val configRespuestaJson = buildConfigRespuestaJson(q)

                            val newId = java.util.UUID.randomUUID()
                            val sql = """
                                INSERT INTO pregunta (
                                    pregunta_id,
                                    tipo_banco,
                                    sector,
                                    nivel,
                                    texto,
                                    pistas,
                                    config_respuesta
                                )
                                VALUES (
                                    '$newId',
                                    'IAJOB',
                                    '$safeSector',
                                    '$safeNivel',
                                    '$safeTexto',
                                    '$pistasJson'::jsonb,
                                    '$configRespuestaJson'::jsonb
                                )
                            """.trimIndent()

                            TransactionManager.current().exec(sql)
                            println("💾 Insertada pregunta en BD: [${q.tipo}] $safeTexto (nivel=$safeNivel)")
                            totalGuardadas++
                        }
                    }

                    result += JobWithSavedQuestionsDto(
                        job = job,
                        preguntas = preguntasGeneradas.map { it.enunciado }
                    )
                }

                GenerateJobsResponse(
                    message = "Se guardaron $totalGuardadas preguntas generadas automáticamente (IAJOB)",
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
         *   - nivel: jr | medio | sr
         *   - sector: texto (busca exacto en columna sector)
         *   - limit: cantidad máxima de preguntas (por defecto 50)
         *
         * Filtra solo tipo_banco = 'IAJOB'.
         */
        get("/generated-questions") {
            val nivelFilter = call.request.queryParameters["nivel"]  // jr / medio / sr
            val sectorFilter = call.request.queryParameters["sector"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val preguntas: List<PreguntaDto> = transaction {
                val conditions = mutableListOf<String>()

                // Solo tipo_banco = 'IAJOB'
                conditions += "tipo_banco = 'IAJOB'"

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
                        config_respuesta,
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
                        val pistas = rs.getString("pistas")                   // JSON como string
                        val configRespuesta = rs.getString("config_respuesta")// JSON como string
                        val activa = rs.getBoolean("activa")
                        val fechaCreacion = rs.getTimestamp("fecha_creacion")?.toInstant()?.toString() ?: ""

                        list += PreguntaDto(
                            preguntaId = id,
                            tipoBanco = tipoBanco,
                            sector = sector,
                            nivel = nivel,
                            texto = texto,
                            pistas = pistas,
                            configRespuesta = configRespuesta,
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
