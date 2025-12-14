/* src/main/kotlin/routes/jobs/JobsGeneratorRoutes.kt */

package routes.jobs

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import services.JSearchService
import services.InterviewQuestionService
import services.JobNormalizedDto
import services.MixedGeneratedQuestionDto
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// ===================== Constantes =====================

private const val QUESTIONS_PER_JOB = 3          // cuántas preguntas devolvemos al cliente por cada meta_dato
private const val BOOTSTRAP_TOTAL = 30          // total esperado en bootstrap
private const val BOOTSTRAP_PER_LEVEL = 10      // 10 jr, 10 mid, 10 sr
private const val MAX_JOBS = 1                  // cuántos avisos tomamos desde JSearch por meta_dato
private const val BACKUP_FILE_PATH = "src/DB/preguntas_backup.json"

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
    val preguntas: List<String>,      // enunciados devueltos al cliente
    val generadoPorIA: Boolean,       // true si se generaron nuevas preguntas
    val motivo: String? = null        // explicación del flujo
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

/**
 * Determina el nivel (jr / mid / sr) en base al título del aviso (fallback).
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
 * Dado un meta_cargo (metaDato), determina el sector usando los valores del MVP.
 *
 * Si no matchea nada, sector = "Otra área".
 */
private fun inferSectorFromMetaCargo(metaCargo: String): String {
    val t = metaCargo.lowercase()

    return when {
        // Desarrollador
        "desarrollador backend" in t || ("developer" in t && "backend" in t) ->
            "Desarrollador"
        "desarrollador frontend" in t || ("developer" in t && "frontend" in t) ->
            "Desarrollador"
        "desarrollador fullstack" in t || ("developer" in t && ("fullstack" in t || "full stack" in t)) ->
            "Desarrollador"
        "desarrollador android" in t || ("developer" in t && "android" in t) ->
            "Desarrollador"
        "qa automation" in t ->
            "Desarrollador"

        // Analista TI
        "soporte ti" in t || "helpdesk" in t || "help desk" in t ->
            "Analista TI"
        "devops" in t ->
            "Analista TI"
        "sysadmin" in t || "administrador de sistemas" in t ->
            "Analista TI"

        // Analista
        "analista de datos" in t || "data analyst" in t ->
            "Analista"
        "analista de negocios" in t || "business analyst" in t ->
            "Analista"
        "analista qa" in t ->
            "Analista"
        "analista funcional" in t ->
            "Analista"

        // Administración
        "asistente administrativo" in t || "administrativo" in t ->
            "Administracion"
        "analista contable" in t || "contador" in t ->
            "Administracion"
        "jefe de administración" in t || "jefe de administracion" in t ->
            "Administracion"

        else -> "Otra área"
    }
}

/**
 * Convierte lista de pistas (List<String>) a JSON string seguro para insertar.
 */
private fun buildPistasJson(pistas: List<String>): String {
    if (pistas.isEmpty()) return "[]"
    val safeHints = pistas.map { it.replace("\"", "\\\"") }
    return safeHints.joinToString(
        separator = "\", \"",
        prefix = "[\"",
        postfix = "\"]"
    )
}

/**
 * Construye el JSON de config_respuesta SIN incluir el campo "tipo".
 */
private fun buildConfigRespuestaJson(q: MixedGeneratedQuestionDto): String {
    return if (q.tipo == "seleccion_unica") {
        // Pregunta cerrada con alternativas
        val safeOpciones = q.opciones.map { opt ->
            val safeTexto = opt.texto.replace("\"", "\\\"")
            """{"id":"${opt.id}","texto":"$safeTexto"}"""
        }

        val opcionesJoined = safeOpciones.joinToString(
            separator = ",",
            prefix = "[",
            postfix = "]"
        )

        val correct = q.respuesta_correcta ?: ""

        """
        {
          "opciones": $opcionesJoined,
          "respuesta_correcta": "$correct"
        }
        """.trimIndent().replace("\n", "")
    } else {
        // Pregunta abierta
        val min = q.min_caracteres ?: 20
        val max = q.max_caracteres ?: 300

        """
        {
          "min_caracteres": $min,
          "max_caracteres": $max
        }
        """.trimIndent().replace("\n", "")
    }
}

/**
 * 🆕 Construye el JSON de config_evaluacion según el tipo de pregunta.
 */
private fun buildConfigEvaluacionJson(q: MixedGeneratedQuestionDto): String {
    return if (q.tipo == "seleccion_unica") {
        // Pregunta de selección única (choice)
        val safeExplicacionCorrecta = q.explicacion_correcta
            ?.replace("\"", "\\\"")
            ?.replace("\n", " ") ?: ""
        
        val safeExplicacionIncorrecta = q.explicacion_incorrecta
            ?.replace("\"", "\\\"")
            ?.replace("\n", " ") ?: ""
        
        """
        {
          "tipo_item": "choice",
          "nlp": {
            "explicacion_correcta": "$safeExplicacionCorrecta",
            "explicacion_incorrecta": "$safeExplicacionIncorrecta"
          }
        }
        """.trimIndent().replace("\n", "")
    } else {
        // Pregunta abierta (open)
        val safeFeedback = q.feedback_generico
            ?.replace("\"", "\\\"")
            ?.replace("\n", " ") ?: ""
        
        val safeFrases = q.frases_clave_esperadas.map { 
            it.replace("\"", "\\\"") 
        }
        
        val frasesJoined = if (safeFrases.isEmpty()) {
            "[]"
        } else {
            safeFrases.joinToString(
                separator = "\", \"",
                prefix = "[\"",
                postfix = "\"]"
            )
        }
        
        """
        {
          "tipo_item": "open",
          "nlp": {
            "frases_clave_esperadas": $frasesJoined
          },
          "feedback_generico": "$safeFeedback"
        }
        """.trimIndent().replace("\n", "")
    }
}

/**
 * 🆕 Guarda la pregunta en el archivo de backup JSON
 */
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
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
        
        // Crear directorio si no existe
        backupFile.parentFile?.mkdirs()
        
        // Leer backup existente o crear uno nuevo
        val container: BackupContainer = if (backupFile.exists()) {
            try {
                json.decodeFromString(backupFile.readText())
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
        
        // Crear nueva pregunta
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
        
        // Agregar a la lista (evitar duplicados por UUID)
        val preguntasActualizadas = container.preguntas
            .filterNot { it.pregunta_id == preguntaId.toString() }
            .plus(nuevaPregunta)
        
        // Actualizar container
        val containerActualizado = container.copy(
            fecha_actualizacion = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
            total_preguntas = preguntasActualizadas.size,
            preguntas = preguntasActualizadas
        )
        
        // Escribir archivo
        backupFile.writeText(json.encodeToString(containerActualizado))
        
        println("✅ Pregunta ${preguntaId} guardada en backup (total: ${preguntasActualizadas.size})")
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

        /**
         * POST /jobs/generate-and-save
         *
         * Body esperado:
         * {
         *   "meta_dato": "Desarrollador Android",
         *   "country": "cl"
         * }
         */
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
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "El campo meta_dato es obligatorio")
                )
                return@post
            }

            try {
                // Usamos metaDato como query hacia JSearch
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

                    // Ahora el meta_cargo viene del request
                    val metaCargoInferido = req.metaDato
                    val sectorInferido = inferSectorFromMetaCargo(metaCargoInferido)

                    val safeSector = sectorInferido.replace("'", "''")
                    val safeMetaCargo = metaCargoInferido.replace("'", "''")
                    val safeNivelBase = nivelInferido.replace("'", "''")

                    // ----------------------------------------------------------------
                    // 0) ¿Existe ALGUNA pregunta para este (sector, meta_cargo)?
                    //    (cualquier nivel, cualquier tipo_banco)
                    // ----------------------------------------------------------------
                    val existeBancoParaSectorMeta = transaction {
                        var count = 0
                        val sql = """
                            SELECT COUNT(*) AS cnt
                            FROM pregunta
                            WHERE sector = '$safeSector'
                            AND meta_cargo = '$safeMetaCargo'
                            AND tipo_banco IN ('AIJOB', 'PR')   -- Solo buscamos 'AIJOB' o 'PR'
                        """.trimIndent()

                        TransactionManager.current().exec(sql) { rs ->
                            if (rs.next()) {
                                count = rs.getInt("cnt")
                            }
                        }
                        count > 0   // Retorna true si hay al menos una pregunta que cumple con las condiciones
                    }

                    if (!existeBancoParaSectorMeta) {
                        // =========================================================
                        // Caso A: NO hay nada aún para este sector/meta_cargo
                        //         -> Crear banco inicial:
                        //            10 jr, 10 mid, 10 sr (total 30)
                        // =========================================================
                        println("🆕 No existe banco para sector=$safeSector, meta_cargo=$safeMetaCargo. Generando banco inicial (10 jr, 10 mid, 10 sr)...")

                        val nivelesBootstrap = listOf("jr", "mid", "sr")
                        val preguntasBootstrap = mutableListOf<Pair<MixedGeneratedQuestionDto, String>>()

                        for (nivelTarget in nivelesBootstrap) {
                            var acumuladasNivel = 0

                            // Loop para asegurar hasta 10 por nivel (si la IA coopera)
                            while (acumuladasNivel < BOOTSTRAP_PER_LEVEL) {
                                val batch = interviewQuestionService.generateMixedQuestionsForJob(
                                    job = job,
                                    cantidad = 1 // pedimos 1 para no depender de que respete el "cantidad"
                                )

                                println("   🔧 IA nivel=$nivelTarget devolvió ${batch.size} preguntas (acumuladasNivel=$acumuladasNivel)")

                                if (batch.isEmpty()) {
                                    // Evita loop infinito si la IA empieza a devolver 0
                                    println("   ⚠️ La IA devolvió 0 preguntas para nivel=$nivelTarget, se detiene el loop de este nivel.")
                                    break
                                }

                                batch.forEach { q ->
                                    preguntasBootstrap += q to nivelTarget
                                    acumuladasNivel++

                                    if (acumuladasNivel >= BOOTSTRAP_PER_LEVEL) return@forEach
                                }
                            }
                        }

                        println("✅ Total preguntas generadas en bootstrap para meta_cargo=$safeMetaCargo: ${preguntasBootstrap.size}")

                        transaction {
                            preguntasBootstrap.forEach { (q, nivelTarget) ->
                                val safeTexto = q.enunciado.replace("'", "''")

                                // Nivel forzado por batch (jr / mid / sr)
                                val safeNivel = nivelTarget.replace("'", "''")

                                val tipoPreguntaDb = if (q.tipo == "seleccion_unica") {
                                    "opcion_multiple"
                                } else {
                                    "abierta"
                                }

                                val pistasJson = buildPistasJson(q.pistas)
                                val configRespuestaJson = buildConfigRespuestaJson(q)
                                val configEvaluacionJson = buildConfigEvaluacionJson(q)  // 🆕 NUEVA LÍNEA

                                val newId = java.util.UUID.randomUUID()
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
                                    VALUES (
                                        '$newId',
                                        'IAJOB',
                                        '$safeSector',
                                        '$safeNivel',
                                        '$safeMetaCargo',
                                        '$tipoPreguntaDb',
                                        '$safeTexto',
                                        '$pistasJson'::jsonb,
                                        '$configRespuestaJson'::jsonb,
                                        '$configEvaluacionJson'::jsonb,
                                        true,
                                        now()
                                    )
                                """.trimIndent()

                                TransactionManager.current().exec(sql)
                                println("💾 [Bootstrap] Insertada pregunta sector=$safeSector, meta_cargo=$safeMetaCargo, nivel=$safeNivel")
                                
                                // 🆕 Guardar en backup
                                guardarPreguntaEnBackup(
                                    preguntaId = newId,
                                    tipoBanco = "IAJOB",
                                    sector = safeSector,
                                    nivel = safeNivel,
                                    metaCargo = safeMetaCargo,
                                    tipoPregunta = tipoPreguntaDb,
                                    texto = safeTexto,
                                    pistasJson = pistasJson,
                                    configRespuestaJson = configRespuestaJson,
                                    configEvaluacionJson = configEvaluacionJson
                                )
                                
                                totalGuardadas++
                            }
                        }

                        // Devolvemos al cliente solo las primeras QUESTIONS_PER_JOB preguntas como ejemplo
                        result += JobWithSavedQuestionsDto(
                            job = job,
                            preguntas = preguntasBootstrap
                                .map { it.first.enunciado }
                                .take(QUESTIONS_PER_JOB),
                            generadoPorIA = true,
                            motivo = "Se creó banco inicial de hasta $BOOTSTRAP_TOTAL preguntas (10 jr, 10 mid, 10 sr) para este sector y meta_cargo, porque no existían preguntas previas."
                        )

                        // Pasamos al siguiente aviso
                        continue
                    }

                    // =========================================================
                    // Caso B: YA hay banco para este sector/meta_cargo
                    //         -> Intentamos reutilizar por nivel
                    // =========================================================
                    val preguntasExistentes: List<String> = transaction {
                        val existing = mutableListOf<String>()
                        val sql = """
                            SELECT texto
                            FROM pregunta
                            WHERE sector = '$safeSector'
                            AND meta_cargo = '$safeMetaCargo'
                            AND nivel = '$safeNivelBase'
                            AND tipo_banco <> 'NV'
                            ORDER BY fecha_creacion DESC
                            LIMIT $QUESTIONS_PER_JOB
                        """.trimIndent()

                        TransactionManager.current().exec(sql) { rs ->
                            while (rs.next()) {
                                val texto = rs.getString("texto")
                                if (!texto.isNullOrBlank()) {
                                    existing += texto
                                }
                            }
                        }

                        existing
                    }

                    if (preguntasExistentes.isNotEmpty()) {
                        println("🔁 Reutilizando ${preguntasExistentes.size} preguntas existentes para sector=$safeSector, meta_cargo=$safeMetaCargo, nivel=$safeNivelBase")

                        result += JobWithSavedQuestionsDto(
                            job = job,
                            preguntas = preguntasExistentes,
                            generadoPorIA = false,
                            motivo = "No se generaron preguntas nuevas porque ya existen preguntas para este sector, meta_cargo y nivel."
                        )
                        continue
                    }

                    // =========================================================
                    // Caso C: Sí hay banco para el sector/meta_cargo,
                    //         pero no para este nivel específico -> generamos
                    // =========================================================
                    val preguntasGeneradas = interviewQuestionService.generateMixedQuestionsForJob(
                        job = job,
                        cantidad = QUESTIONS_PER_JOB
                    )

                    println("\n🎯 Aviso: ${job.titulo} (${job.empresa ?: "Sin empresa"})")
                    preguntasGeneradas.forEachIndexed { idx, p ->
                        println("   ❓ [${idx + 1}] (${p.tipo}) ${p.enunciado}")
                    }

                    transaction {
                        preguntasGeneradas.forEach { q ->
                            val safeTexto = q.enunciado.replace("'", "''")
                            val rawNivel = q.nivel ?: nivelInferido
                            val safeNivel = when (rawNivel.lowercase()) {
                                "medio", "intermedio", "semi", "ssr", "middle" -> "mid"
                                "senior", "sr" -> "sr"
                                else -> "jr"
                            }.replace("'", "''")

                            val tipoPreguntaDb = if (q.tipo == "seleccion_unica") {
                                "opcion_multiple"
                            } else {
                                "abierta"
                            }

                            val pistasJson = buildPistasJson(q.pistas)
                            val configRespuestaJson = buildConfigRespuestaJson(q)
                            val configEvaluacionJson = buildConfigEvaluacionJson(q)  // 🆕 NUEVA LÍNEA

                            val newId = java.util.UUID.randomUUID()
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
                                VALUES (
                                    '$newId',
                                    'IAJOB',
                                    '$safeSector',
                                    '$safeNivel',
                                    '$safeMetaCargo',
                                    '$tipoPreguntaDb',
                                    '$safeTexto',
                                    '$pistasJson'::jsonb,
                                    '$configRespuestaJson'::jsonb,
                                    '$configEvaluacionJson'::jsonb,
                                    true,
                                    now()
                                )
                            """.trimIndent()

                            TransactionManager.current().exec(sql)
                            println("💾 Insertada pregunta en BD: [${q.tipo}] sector=$safeSector, meta_cargo=$safeMetaCargo, nivel=$safeNivel, texto=$safeTexto")
                            
                            // 🆕 Guardar en backup
                            guardarPreguntaEnBackup(
                                preguntaId = newId,
                                tipoBanco = "IAJOB",
                                sector = safeSector,
                                nivel = safeNivel,
                                metaCargo = safeMetaCargo,
                                tipoPregunta = tipoPreguntaDb,
                                texto = safeTexto,
                                pistasJson = pistasJson,
                                configRespuestaJson = configRespuestaJson,
                                configEvaluacionJson = configEvaluacionJson
                            )
                            
                            totalGuardadas++
                        }
                    }

                    result += JobWithSavedQuestionsDto(
                        job = job,
                        preguntas = preguntasGeneradas.map { it.enunciado },
                        generadoPorIA = true,
                        motivo = "Se generaron preguntas con IA (IAJOB) para este nivel porque ya existía banco para el sector/meta_cargo, pero no para este nivel."
                    )
                }

                val responsePayload = GenerateJobsResponse(
                    message = "Se guardaron $totalGuardadas preguntas generadas automáticamente (IAJOB)",
                    totalEmpleosProcesados = result.size,
                    items = result
                )

                call.respond(responsePayload)
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

        /**
         * GET /jobs/generated-questions
         */
        get("/generated-questions") {
            val nivelFilter = call.request.queryParameters["nivel"]  // jr / mid / sr
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
                        val pistas = rs.getString("pistas")
                        val configRespuesta = rs.getString("config_respuesta")
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