/* src/main/kotlin/routes/jobs/JobsGeneratorRoutes.kt */

package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PGobject
import services.JSearchService
import services.InterviewQuestionService
import services.JobNormalizedDto
import services.MixedGeneratedQuestionDto
import java.io.File
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// ===================== Constantes =====================

private const val QUESTIONS_PER_JOB = 3
private const val BOOTSTRAP_TOTAL = 30
private const val BOOTSTRAP_PER_LEVEL = 10
private const val MAX_JOBS = 1
private const val BACKUP_FILE_PATH = "src/DB/preguntas_backup.json"

// Solo estos bancos cuentan para “prueba”
private val BANKS_FOR_TEST = listOf("AIJOB", "PR")

// JSON (único) para todo el archivo
private val JSON = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

// ===================== DTOs =====================

@Serializable
data class GenerateJobsRequest(
    @SerialName("meta_dato")
    val metaDato: String,
    val country: String? = "cl"
)

@Serializable
data class JobWithSavedQuestionsDto(
    val job: JobNormalizedDto,
    val preguntas: List<String>,
    val generadoPorIA: Boolean,
    val motivo: String? = null
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
    val pistas: String?,
    val configRespuesta: String?,
    val activa: Boolean,
    val fechaCreacion: String
)

// 🆕 DTOs para backup
@Serializable
data class PreguntaBackup(
    val pregunta_id: String,
    val tipo_banco: String,
    val sector: String,
    val nivel: String,
    val meta_cargo: String,
    val tipo_pregunta: String,
    val texto: String,
    val pistas: String,
    val config_respuesta: String,
    val config_evaluacion: String?,
    val fecha_creacion: String
)

@Serializable
data class BackupContainer(
    val version: String = "1.0",
    val fecha_actualizacion: String,
    val descripcion: String = "Backup de preguntas generadas por IA para restauración en caso de pérdida de datos",
    val total_preguntas: Int,
    val preguntas: List<PreguntaBackup>
)

// ===================== Helpers =====================

private fun inferNivelFromTitle(titulo: String): String {
    val t = titulo.lowercase()
    return when {
        "senior" in t || " sr" in t || "sr " in t -> "sr"
        "semi" in t || "medio" in t || "middle" in t || "ssr" in t -> "mid"
        "junior" in t || " jr" in t || "jr " in t || "trainee" in t -> "jr"
        else -> "jr"
    }
}

private fun inferSectorFromMetaCargo(metaCargo: String): String {
    val t = metaCargo.lowercase()
    return when {
        // Desarrollador
        "desarrollador backend" in t || ("developer" in t && "backend" in t) -> "Desarrollador"
        "desarrollador frontend" in t || ("developer" in t && "frontend" in t) -> "Desarrollador"
        "desarrollador fullstack" in t || ("developer" in t && ("fullstack" in t || "full stack" in t)) -> "Desarrollador"
        "desarrollador android" in t || ("developer" in t && "android" in t) -> "Desarrollador"
        "qa automation" in t -> "Desarrollador"

        // Analista TI
        "soporte ti" in t || "helpdesk" in t || "help desk" in t -> "Analista TI"
        "devops" in t -> "Analista TI"
        "sysadmin" in t || "administrador de sistemas" in t -> "Analista TI"

        // Analista
        "analista de datos" in t || "data analyst" in t -> "Analista"
        "analista de negocios" in t || "business analyst" in t -> "Analista"
        "analista qa" in t -> "Analista"
        "analista funcional" in t -> "Analista"

        // Administración
        "asistente administrativo" in t || "administrativo" in t -> "Administracion"
        "analista contable" in t || "contador" in t -> "Administracion"
        "jefe de administración" in t || "jefe de administracion" in t -> "Administracion"

        else -> "Otra área"
    }
}

private fun buildPistasJson(pistas: List<String>): String =
    JSON.encodeToString(pistas)

/**
 * FIX: NO usar Map<String, Any> porque kotlinx.serialization no puede serializar Any.
 * Construimos JsonElement (buildJsonObject/buildJsonArray) y serializamos JsonElement.
 */
private fun buildConfigRespuestaJson(q: MixedGeneratedQuestionDto): String {
    val elem: JsonElement =
        if (q.tipo == "seleccion_unica") {
            buildJsonObject {
                put(
                    "opciones",
                    buildJsonArray {
                        q.opciones.forEach { opt ->
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(opt.id))
                                    put("texto", JsonPrimitive(opt.texto))
                                }
                            )
                        }
                    }
                )
                put("respuesta_correcta", JsonPrimitive(q.respuesta_correcta ?: ""))
            }
        } else {
            val min = q.min_caracteres ?: 20
            val max = q.max_caracteres ?: 300
            buildJsonObject {
                put("min_caracteres", JsonPrimitive(min))
                put("max_caracteres", JsonPrimitive(max))
            }
        }

    return JSON.encodeToString(JsonElement.serializer(), elem)
}

/**
 * FIX: idem, evitar Any.
 */
private fun buildConfigEvaluacionJson(q: MixedGeneratedQuestionDto): String {
    val elem: JsonElement =
        if (q.tipo == "seleccion_unica") {
            buildJsonObject {
                put("tipo_item", JsonPrimitive("choice"))
                put(
                    "nlp",
                    buildJsonObject {
                        put("explicacion_correcta", JsonPrimitive(q.explicacion_correcta ?: ""))
                        put("explicacion_incorrecta", JsonPrimitive(q.explicacion_incorrecta ?: ""))
                    }
                )
            }
        } else {
            buildJsonObject {
                put("tipo_item", JsonPrimitive("open"))
                put(
                    "nlp",
                    buildJsonObject {
                        put(
                            "frases_clave_esperadas",
                            buildJsonArray {
                                (q.frases_clave_esperadas ?: emptyList()).forEach { s ->
                                    add(JsonPrimitive(s))
                                }
                            }
                        )
                    }
                )
                put("feedback_generico", JsonPrimitive(q.feedback_generico ?: ""))
            }
        }

    return JSON.encodeToString(JsonElement.serializer(), elem)
}

private fun toJsonb(json: String): PGobject =
    PGobject().apply {
        type = "jsonb"
        value = json
    }

/**
 * FIX REAL (runtime):
 * TransactionManager.current().connection es JdbcConnectionImpl (Exposed), NO java.sql.Connection.
 * JdbcConnectionImpl sí expone la JDBC connection real vía `.connection`.
 */
private fun jdbcConnection(): Connection {
    val exposedConn = TransactionManager.current().connection
    return when (exposedConn) {
        is JdbcConnectionImpl -> exposedConn.connection
        is Connection -> exposedConn
        else -> error("No se pudo obtener java.sql.Connection desde Exposed connection: ${exposedConn::class.qualifiedName}")
    }
}

private fun insertPreguntaSafe(
    preguntaId: UUID,
    tipoBanco: String,
    sector: String,
    nivel: String,
    metaCargo: String,
    tipoPreguntaDb: String,
    texto: String,
    pistasJson: String,
    configRespuestaJson: String,
    configEvaluacionJson: String?
) {
    val sql = """
        INSERT INTO pregunta (
            pregunta_id,
            tipo_banco,
            sector,
            nivel,
            meta_cargo,
            tipo_pregunta,
            texto,
            pistas,
            config_respuesta,
            config_evaluacion,
            activa,
            fecha_creacion
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, now())
    """.trimIndent()

    val conn = jdbcConnection()
    val stmt = conn.prepareStatement(sql)
    try {
        stmt.setObject(1, preguntaId)
        stmt.setString(2, tipoBanco)
        stmt.setString(3, sector)
        stmt.setString(4, nivel)
        stmt.setString(5, metaCargo)
        stmt.setString(6, tipoPreguntaDb)
        stmt.setString(7, texto)

        stmt.setObject(8, toJsonb(pistasJson))
        stmt.setObject(9, toJsonb(configRespuestaJson))

        if (configEvaluacionJson == null) {
            stmt.setNull(10, Types.OTHER)
        } else {
            stmt.setObject(10, toJsonb(configEvaluacionJson))
        }

        stmt.executeUpdate()
    } finally {
        try { stmt.close() } catch (_: Exception) {}
    }
}

private fun countBancoParaSectorMeta(sector: String, metaCargo: String): Int {
    val sql = """
        SELECT COUNT(*) AS cnt
        FROM pregunta
        WHERE sector = ?
          AND meta_cargo = ?
          AND tipo_banco IN ('AIJOB', 'PR')
    """.trimIndent()

    val conn = jdbcConnection()
    val stmt = conn.prepareStatement(sql)
    try {
        stmt.setString(1, sector)
        stmt.setString(2, metaCargo)

        val rs = stmt.executeQuery()
        try {
            return if (rs.next()) rs.getInt("cnt") else 0
        } finally {
            try { rs.close() } catch (_: Exception) {}
        }
    } finally {
        try { stmt.close() } catch (_: Exception) {}
    }
}

private fun fetchPreguntasExistentes(
    sector: String,
    metaCargo: String,
    nivel: String,
    limit: Int
): List<String> {
    val sql = """
        SELECT texto
        FROM pregunta
        WHERE sector = ?
          AND meta_cargo = ?
          AND nivel = ?
          AND tipo_banco IN ('AIJOB', 'PR')
        ORDER BY fecha_creacion DESC
        LIMIT ?
    """.trimIndent()

    val out = mutableListOf<String>()

    val conn = jdbcConnection()
    val stmt = conn.prepareStatement(sql)
    try {
        stmt.setString(1, sector)
        stmt.setString(2, metaCargo)
        stmt.setString(3, nivel)
        stmt.setInt(4, limit)

        val rs = stmt.executeQuery()
        try {
            while (rs.next()) {
                val t = rs.getString("texto")
                if (!t.isNullOrBlank()) out.add(t)
            }
        } finally {
            try { rs.close() } catch (_: Exception) {}
        }
    } finally {
        try { stmt.close() } catch (_: Exception) {}
    }

    return out
}

private fun guardarPreguntaEnBackup(
    preguntaId: UUID,
    tipoBanco: String,
    sector: String,
    nivel: String,
    metaCargo: String,
    tipoPregunta: String,
    texto: String,
    pistasJson: String,
    configRespuestaJson: String,
    configEvaluacionJson: String?
) {
    try {
        val backupFile = File(BACKUP_FILE_PATH)
        backupFile.parentFile?.mkdirs()

        val container: BackupContainer = if (backupFile.exists()) {
            try {
                val raw = backupFile.readText(StandardCharsets.UTF_8)
                JSON.decodeFromString(raw)
            } catch (e: Exception) {
                println("⚠️ Error leyendo backup existente, creando nuevo: ${e.message}")
                BackupContainer(
                    fecha_actualizacion = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                    total_preguntas = 0,
                    preguntas = emptyList()
                )
            }
        } else {
            BackupContainer(
                fecha_actualizacion = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                total_preguntas = 0,
                preguntas = emptyList()
            )
        }

        val nuevaPregunta = PreguntaBackup(
            pregunta_id = preguntaId.toString(),
            tipo_banco = tipoBanco,
            sector = sector,
            nivel = nivel,
            meta_cargo = metaCargo,
            tipo_pregunta = tipoPregunta,
            texto = texto,
            pistas = pistasJson,
            config_respuesta = configRespuestaJson,
            config_evaluacion = configEvaluacionJson,
            fecha_creacion = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
        )

        val preguntasActualizadas = container.preguntas
            .filterNot { it.pregunta_id == preguntaId.toString() }
            .plus(nuevaPregunta)

        val containerActualizado = container.copy(
            fecha_actualizacion = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            total_preguntas = preguntasActualizadas.size,
            preguntas = preguntasActualizadas
        )

        backupFile.writeText(JSON.encodeToString(containerActualizado), StandardCharsets.UTF_8)
        println("✅ Pregunta $preguntaId guardada en backup (total: ${preguntasActualizadas.size})")
    } catch (e: Exception) {
        println("⚠️ Error guardando backup: ${e.message}")
        e.printStackTrace()
    }
}

// ===================== Rutas =====================

fun Route.jobsGeneratorRoutes(
    jSearchService: JSearchService,
    interviewQuestionService: InterviewQuestionService
) {
    route("/jobs") {

        post("/generate-and-save") {
            val req = try {
                call.receive<GenerateJobsRequest>()
            } catch (e: Exception) {
                e.printStackTrace()
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf(
                        "error" to "Body inválido",
                        "example" to """{ "meta_dato": "Desarrollador Android", "country": "cl" }"""
                    )
                )
                return@post
            }

            if (req.metaDato.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "El campo meta_dato es obligatorio"))
                return@post
            }

            try {
                val jobs = jSearchService.searchJobs(
                    query = req.metaDato,
                    country = req.country,
                    page = 1
                )

                println("📌 Encontrados ${jobs.size} empleos para meta_dato='${req.metaDato}'")

                val subset = jobs.take(MAX_JOBS)
                var totalGuardadas = 0

                val result = mutableListOf<JobWithSavedQuestionsDto>()

                for (job in subset) {
                    val nivelInferido = inferNivelFromTitle(job.titulo)

                    val metaCargo = req.metaDato
                    val sector = inferSectorFromMetaCargo(metaCargo)
                    val nivelBase = nivelInferido

                    val existeBancoParaSectorMeta = transaction {
                        countBancoParaSectorMeta(sector = sector, metaCargo = metaCargo) > 0
                    }

                    if (!existeBancoParaSectorMeta) {
                        println("🆕 No existe banco para sector=$sector, meta_cargo=$metaCargo. Generando banco inicial (10 jr, 10 mid, 10 sr)...")

                        val nivelesBootstrap = listOf("jr", "mid", "sr")
                        val preguntasBootstrap = mutableListOf<Pair<MixedGeneratedQuestionDto, String>>()

                        for (nivelTarget in nivelesBootstrap) {
                            var acumuladasNivel = 0
                            while (acumuladasNivel < BOOTSTRAP_PER_LEVEL) {
                                val batch = interviewQuestionService.generateMixedQuestionsForJob(
                                    job = job,
                                    cantidad = 1
                                )

                                println("   🔧 IA nivel=$nivelTarget devolvió ${batch.size} preguntas (acumuladasNivel=$acumuladasNivel)")

                                if (batch.isEmpty()) {
                                    println("   ⚠️ IA devolvió 0 preguntas para nivel=$nivelTarget, se detiene este nivel.")
                                    break
                                }

                                for (q in batch) {
                                    preguntasBootstrap.add(q to nivelTarget)
                                    acumuladasNivel++
                                    if (acumuladasNivel >= BOOTSTRAP_PER_LEVEL) break
                                }
                            }
                        }

                        println("✅ Total preguntas generadas en bootstrap para meta_cargo=$metaCargo: ${preguntasBootstrap.size}")

                        transaction {
                            for ((q, nivelTarget) in preguntasBootstrap) {
                                val tipoPreguntaDb = if (q.tipo == "seleccion_unica") "opcion_multiple" else "abierta"

                                val pistasJson = buildPistasJson(q.pistas)
                                val configRespuestaJson = buildConfigRespuestaJson(q)
                                val configEvaluacionJson = buildConfigEvaluacionJson(q)

                                val newId = UUID.randomUUID()

                                insertPreguntaSafe(
                                    preguntaId = newId,
                                    tipoBanco = "IAJOB",
                                    sector = sector,
                                    nivel = nivelTarget,
                                    metaCargo = metaCargo,
                                    tipoPreguntaDb = tipoPreguntaDb,
                                    texto = q.enunciado,
                                    pistasJson = pistasJson,
                                    configRespuestaJson = configRespuestaJson,
                                    configEvaluacionJson = configEvaluacionJson
                                )

                                guardarPreguntaEnBackup(
                                    preguntaId = newId,
                                    tipoBanco = "IAJOB",
                                    sector = sector,
                                    nivel = nivelTarget,
                                    metaCargo = metaCargo,
                                    tipoPregunta = tipoPreguntaDb,
                                    texto = q.enunciado,
                                    pistasJson = pistasJson,
                                    configRespuestaJson = configRespuestaJson,
                                    configEvaluacionJson = configEvaluacionJson
                                )

                                totalGuardadas++
                            }
                        }

                        result.add(
                            JobWithSavedQuestionsDto(
                                job = job,
                                preguntas = preguntasBootstrap.map { it.first.enunciado }.take(QUESTIONS_PER_JOB),
                                generadoPorIA = true,
                                motivo = "Se creó banco inicial de hasta $BOOTSTRAP_TOTAL preguntas (10 jr, 10 mid, 10 sr) para este sector y meta_cargo, porque no existían preguntas previas (PR/AIJOB)."
                            )
                        )

                        continue
                    }

                    val preguntasExistentes: List<String> = transaction {
                        fetchPreguntasExistentes(
                            sector = sector,
                            metaCargo = metaCargo,
                            nivel = nivelBase,
                            limit = QUESTIONS_PER_JOB
                        )
                    }

                    if (preguntasExistentes.isNotEmpty()) {
                        result.add(
                            JobWithSavedQuestionsDto(
                                job = job,
                                preguntas = preguntasExistentes,
                                generadoPorIA = false,
                                motivo = "No se generaron preguntas nuevas porque ya existen preguntas (PR/AIJOB) para este sector, meta_cargo y nivel."
                            )
                        )
                        continue
                    }

                    val preguntasGeneradas = interviewQuestionService.generateMixedQuestionsForJob(
                        job = job,
                        cantidad = QUESTIONS_PER_JOB
                    )

                    transaction {
                        for (q in preguntasGeneradas) {
                            val rawNivel = q.nivel ?: nivelInferido
                            val nivelNormalizado = when (rawNivel.lowercase()) {
                                "medio", "intermedio", "semi", "ssr", "middle" -> "mid"
                                "senior", "sr" -> "sr"
                                else -> "jr"
                            }

                            val tipoPreguntaDb = if (q.tipo == "seleccion_unica") "opcion_multiple" else "abierta"

                            val pistasJson = buildPistasJson(q.pistas)
                            val configRespuestaJson = buildConfigRespuestaJson(q)
                            val configEvaluacionJson = buildConfigEvaluacionJson(q)

                            val newId = UUID.randomUUID()

                            insertPreguntaSafe(
                                preguntaId = newId,
                                tipoBanco = "IAJOB",
                                sector = sector,
                                nivel = nivelNormalizado,
                                metaCargo = metaCargo,
                                tipoPreguntaDb = tipoPreguntaDb,
                                texto = q.enunciado,
                                pistasJson = pistasJson,
                                configRespuestaJson = configRespuestaJson,
                                configEvaluacionJson = configEvaluacionJson
                            )

                            guardarPreguntaEnBackup(
                                preguntaId = newId,
                                tipoBanco = "IAJOB",
                                sector = sector,
                                nivel = nivelNormalizado,
                                metaCargo = metaCargo,
                                tipoPregunta = tipoPreguntaDb,
                                texto = q.enunciado,
                                pistasJson = pistasJson,
                                configRespuestaJson = configRespuestaJson,
                                configEvaluacionJson = configEvaluacionJson
                            )

                            totalGuardadas++
                        }
                    }

                    result.add(
                        JobWithSavedQuestionsDto(
                            job = job,
                            preguntas = preguntasGeneradas.map { it.enunciado },
                            generadoPorIA = true,
                            motivo = "Se generaron preguntas con IA (IAJOB) para este nivel porque ya existía banco (PR/AIJOB) para el sector/meta_cargo, pero no para este nivel."
                        )
                    )
                }

                call.respond(
                    GenerateJobsResponse(
                        message = "Se guardaron $totalGuardadas preguntas generadas automáticamente (IAJOB)",
                        totalEmpleosProcesados = result.size,
                        items = result
                    )
                )
            } catch (e: Exception) {
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

        get("/generated-questions") {
            val nivelFilter = call.request.queryParameters["nivel"]
            val sectorFilter = call.request.queryParameters["sector"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50

            val preguntas: List<PreguntaDto> = transaction {
                val conditions = mutableListOf<String>()
                val params = mutableListOf<Any>()

                conditions.add("tipo_banco = ?")
                params.add("IAJOB")

                if (!nivelFilter.isNullOrBlank()) {
                    conditions.add("nivel = ?")
                    params.add(nivelFilter)
                }

                if (!sectorFilter.isNullOrBlank()) {
                    conditions.add("sector = ?")
                    params.add(sectorFilter)
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
                    LIMIT ?
                """.trimIndent()

                val list = mutableListOf<PreguntaDto>()

                val conn = jdbcConnection()
                val stmt = conn.prepareStatement(sql)
                try {
                    var i = 1
                    for (p in params) {
                        when (p) {
                            is String -> stmt.setString(i++, p)
                            is Int -> stmt.setInt(i++, p)
                            else -> stmt.setObject(i++, p)
                        }
                    }
                    stmt.setInt(i, limit)

                    val rs = stmt.executeQuery()
                    try {
                        while (rs.next()) {
                            val id = rs.getObject("pregunta_id")?.toString() ?: ""
                            val tipoBanco = rs.getString("tipo_banco")
                            val sector = rs.getString("sector")
                            val nivel = rs.getString("nivel")
                            val texto = rs.getString("texto")
                            val pistas = rs.getString("pistas")
                            val configRespuesta = rs.getString("config_respuesta")
                            val activa = rs.getBoolean("activa")
                            val fechaCreacion = rs.getTimestamp("fecha_creacion")?.toInstant()?.toString() ?: ""

                            list.add(
                                PreguntaDto(
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
                            )
                        }
                    } finally {
                        try { rs.close() } catch (_: Exception) {}
                    }
                } finally {
                    try { stmt.close() } catch (_: Exception) {}
                }

                list
            }

            call.respond(preguntas)
        }
    }
}
