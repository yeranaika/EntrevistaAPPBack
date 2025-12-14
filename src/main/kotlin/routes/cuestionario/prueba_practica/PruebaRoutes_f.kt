/* src/main/kotlin/routes/cuestionario/prueba_practica/PruebaRoutes_f.kt */

package routes.cuestionario.prueba_practica

import data.repository.billing.SuscripcionRepository
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.Random
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.lowerCase
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

// =============================
//  Tablas Exposed (locales)
// =============================

object PruebaTable : Table("prueba") {
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }

    // En BD suele ser VARCHAR(8): "practica", "nivel", "blended", "simulacion", etc.
    val tipoPrueba = varchar("tipo_prueba", 16)

    // sector → se guarda en area, limite 80
    val area = varchar("area", 80).nullable()

    val nivel = varchar("nivel", 3).nullable()

    // metadata como TEXT (sin límite de longitud desde Exposed)
    val metadata = text("metadata").nullable()

    val activo = bool("activo")

    override val primaryKey = PrimaryKey(pruebaId)
}

object PreguntaTable : Table("pregunta") {
    val preguntaId = uuid("pregunta_id")
    // Bancos: PR (práctica), NV (nivelación), BL (blandas)
    val tipoBanco = varchar("tipo_banco", 5)
    val sector = varchar("sector", 80)
    val nivel = varchar("nivel", 3)               // jr | mid | sr | "1","2","3" en NV

    // tipo_pregunta en BD: "opcion_multiple" | "abierta"
    val tipoPregunta = varchar("tipo_pregunta", 20)

    val texto = text("texto")

    // jsonb en BD, las manejamos como String
    val pistas = text("pistas").nullable()
    val configRespuesta = text("config_respuesta")
    val configEvaluacion = text("config_evaluacion").nullable()   // meta NLP / STAR

    val activa = bool("activa")

    override val primaryKey = PrimaryKey(preguntaId)
}

object PruebaPreguntaTable : Table("prueba_pregunta") {
    val pruebaPreguntaId = uuid("prueba_pregunta_id").clientDefault { UUID.randomUUID() }
    val pruebaId = uuid("prueba_id").references(PruebaTable.pruebaId, onDelete = ReferenceOption.CASCADE)
    val preguntaId = uuid("pregunta_id").references(PreguntaTable.preguntaId, onDelete = ReferenceOption.RESTRICT)
    val orden = integer("orden")
    val opciones = text("opciones").nullable()

    // limite 40; nos aseguramos de truncar al insertar
    val claveCorrecta = varchar("clave_correcta", 40).nullable()

    override val primaryKey = PrimaryKey(pruebaPreguntaId)
}

// =============================
//  DTOs
// =============================

@Serializable
data class CrearPruebaNivelacionReq(
    val usuarioId: String? = null,
    val nombreUsuario: String? = null,
    val sector: String,
    val nivel: String,       // jr | mid | sr
    val metaCargo: String,
    /**
     * Qué tipo de prueba quieres:
     *
     * - "PR"  → solo banco PR
     * - "NV"  → solo banco NV
     * - "BL"  → solo banco BL (habilidades blandas)
     * - "MIX" → mezcla PR + NV + BL
     * - "ENT" → alias de MIX para simulación de entrevista
     */
    val tipoPrueba: String? = null,
    /**
     * Cantidades deseadas PARA MIX / ENT.
     * - Si las dejas nulas ⇒ se usa el comportamiento antiguo (distribución automática).
     * - Si las rellenas ⇒ se valida que:
     *      cantPR + cantNV + cantBL ∈ (0, MAX_PREGUNTAS]
     *      y que ninguna sea negativa.
     *
     * Para PR / NV / BL se ignoran aunque vengan.
     */
    val cantidadPR: Int? = null,
    val cantidadNV: Int? = null,
    val cantidadBL: Int? = null,

    // 🆕 Cuotas por tipo de pregunta (opción múltiple vs abierta)
    val cantidadOpcionMultiple: Int? = null,
    val cantidadAbierta: Int? = null
)

private enum class TipoPreguntaQuotaStrategy {
    CUSTOM,
    PREMIUM_DEFAULT,
    STANDARD_DEFAULT
}

private data class TipoPreguntaQuota(
    val opcionMultiple: Int? = null,
    val abierta: Int? = null,
    val strategy: TipoPreguntaQuotaStrategy = TipoPreguntaQuotaStrategy.CUSTOM,
)

@Serializable
data class PreguntaPruebaDto(
    val preguntaId: String,
    val texto: String,
    val tipoBanco: String,
    val sector: String,
    val nivel: String,
    val tipoPregunta: String,
    val pistas: JsonElement? = null,
    val configRespuesta: JsonElement,
    // opcional: meta de evaluación (NLP + STAR)
    val configEvaluacion: JsonElement? = null,
    val orden: Int
)

@Serializable
data class CrearPruebaNivelacionRes(
    val pruebaId: String,
    val tipoPrueba: String,
    val area: String?,
    val nivel: String?,
    val metadata: Map<String, String>?,
    val preguntas: List<PreguntaPruebaDto>
)

/**
 * POST /api/prueba-practica/front
 *
 * Crea una prueba usando preguntas del banco:
 *   - tipoPrueba = "PR"  → solo tipo_banco = 'PR'
 *   - tipoPrueba = "NV"  → solo tipo_banco = 'NV'
 *   - tipoPrueba = "BL"  → solo tipo_banco = 'BL'
 *   - tipoPrueba = "MIX" o "ENT" → mezcla PR + NV + BL
 *
 * Máximo 10 preguntas, aleatorias.
 *
 * Para MIX / ENT:
 *   - Si NO envías cantidadPR/cantidadNV/cantidadBL → distribución automática.
 *   - Si SÍ envías alguna cantidad → respeta esas cantidades (con validaciones).
 */
fun Route.pruebaFrontRoutes(
    suscripcionRepo: SuscripcionRepository
) {

    post("/api/prueba-practica/front") {
        val req = call.receive<CrearPruebaNivelacionReq>()

        val nivelNormalizado = req.nivel.trim().lowercase()
        val nivelesValidos = setOf("jr", "mid", "sr", "ssr", "1", "2", "3")

        // =========================
        // Validar tipoPrueba
        // =========================
        val modoPrueba = req.tipoPrueba
            ?.trim()
            ?.uppercase()

        val modosValidos = setOf("PR", "NV", "BL", "MIX", "ENT")

        if (modoPrueba == null) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "tipoPrueba es obligatorio y debe ser PR, NV, BL, MIX o ENT"
                )
            )
        }

        if (modoPrueba !in modosValidos) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "tipoPrueba inválido. Debe ser PR, NV, BL, MIX o ENT"
                )
            )
        }

        // Lo que se guarda en la columna tipo_prueba (tipo de PRUEBA, no de banco)
        val etiquetaTipoPrueba = when (modoPrueba) {
            "NV" -> "nivelacion"
            // PR y BL son pruebas prácticas (técnicas o blandas)
            "PR", "BL" -> "practica"
            // MIX / ENT se muestran como entrevistas/simulaciones
            "MIX", "ENT" -> "entrevista"
            else -> "practica"
        }

        if (nivelNormalizado !in nivelesValidos) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "nivel debe ser uno de: jr, mid, sr, ssr, 1, 2, 3")
            )
        }

        if (req.sector.isBlank() || req.metaCargo.isBlank()) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "sector y metaCargo son obligatorios")
            )
        }

        val MAX_PREGUNTAS = 10

        // Optional: verificar si el usuario es premium para habilitar configuraciones avanzadas
        val esPremium: Boolean = req.usuarioId
            ?.takeIf { it.isNotBlank() }
            ?.let { rawId ->
                runCatching { UUID.fromString(rawId) }
                    .getOrNull()
                    ?.let { userUuid ->
                        runCatching { suscripcionRepo.getCurrentStatus(userUuid).isPremium }
                            .getOrDefault(false)
                    }
            }
            ?: false

        if ((req.cantidadOpcionMultiple ?: 0) < 0 || (req.cantidadAbierta ?: 0) < 0) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Las cantidades por tipo de pregunta no pueden ser negativas")
            )
        }

        // ¿Se están usando cantidades personalizadas para MIX / ENT?
        val usarCustomCantidades =
            esPremium &&
                    (modoPrueba == "MIX" || modoPrueba == "ENT") &&
                    (req.cantidadPR != null || req.cantidadNV != null || req.cantidadBL != null)

        // Valores seguros de cantidades (solo se usarán en MIX / ENT)
        val cantPR = if (usarCustomCantidades) req.cantidadPR ?: 0 else 0
        val cantNV = if (usarCustomCantidades) req.cantidadNV ?: 0 else 0
        val cantBL = if (usarCustomCantidades) req.cantidadBL ?: 0 else 0

        val cuotasTipoPregunta = when {
            esPremium && (req.cantidadOpcionMultiple != null || req.cantidadAbierta != null) ->
                TipoPreguntaQuota(
                    opcionMultiple = req.cantidadOpcionMultiple,
                    abierta = req.cantidadAbierta,
                    strategy = TipoPreguntaQuotaStrategy.CUSTOM,
                )

            esPremium ->
                TipoPreguntaQuota(strategy = TipoPreguntaQuotaStrategy.PREMIUM_DEFAULT)

            else ->
                TipoPreguntaQuota(strategy = TipoPreguntaQuotaStrategy.STANDARD_DEFAULT)
        }

        // Validación de cantidades SOLO cuando se envíen en MIX / ENT
        if (usarCustomCantidades) {
            if (cantPR < 0 || cantNV < 0 || cantBL < 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Las cantidades por banco no pueden ser negativas")
                )
            }

            val totalSolicitado = cantPR + cantNV + cantBL

            if (totalSolicitado <= 0) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "La suma de cantidades PR+NV+BL debe ser mayor que 0")
                )
            }

            if (totalSolicitado > MAX_PREGUNTAS) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "La suma de cantidades PR+NV+BL no puede superar $MAX_PREGUNTAS")
                )
            }
        }

        // En metadata:
        // - si es PR/NV/BL guardamos ese banco
        // - si es MIX/ENT guardamos "MIX"
        val tipoBancoMeta = when (modoPrueba) {
            "PR", "NV", "BL" -> modoPrueba
            "MIX", "ENT" -> "MIX"
            else -> modoPrueba
        }

        lateinit var pruebaId: UUID
        val preguntasSeleccionadas = mutableListOf<PreguntaPruebaDto>()

        transaction {
            // 1) Crear fila en PRUEBA

            val areaSafe = req.sector.take(80)
            val metaCargoSafe = req.metaCargo.take(80)

            val metadataJson = buildJsonObject {
                put("metaCargo", JsonPrimitive(metaCargoSafe))
                put("nivelSolicitado", JsonPrimitive(nivelNormalizado))
                put("tipoBanco", JsonPrimitive(tipoBancoMeta))
                put("tipoPruebaEtiqueta", JsonPrimitive(etiquetaTipoPrueba))
                put("usuarioId", JsonPrimitive(req.usuarioId ?: ""))
                put("esPremium", JsonPrimitive(esPremium))
                put("nombreUsuario", JsonPrimitive(req.nombreUsuario ?: ""))
            }.toString()

            pruebaId = PruebaTable.insert {
                it[tipoPrueba] = etiquetaTipoPrueba
                it[area] = areaSafe
                it[nivel] = nivelNormalizado
                it[metadata] = metadataJson
                it[activo] = true
            } get PruebaTable.pruebaId

            fun cuotasPorLimite(limite: Int): Pair<Int?, Int?> {
                if (limite <= 0) return 0 to 0

                return when (cuotasTipoPregunta.strategy) {
                    TipoPreguntaQuotaStrategy.CUSTOM ->
                        (cuotasTipoPregunta.opcionMultiple ?: limite) to (cuotasTipoPregunta.abierta ?: 0)

                    TipoPreguntaQuotaStrategy.PREMIUM_DEFAULT -> {
                        val abiertas = maxOf(1, (limite * 0.4).roundToInt())
                        val opcionMultiple = maxOf(0, limite - abiertas)
                        opcionMultiple to abiertas
                    }

                    TipoPreguntaQuotaStrategy.STANDARD_DEFAULT -> {
                        val abiertas = maxOf(0, (limite * 0.2).roundToInt())
                        val opcionMultiple = maxOf(1, limite - abiertas)
                        opcionMultiple to abiertas
                    }
                }
            }

            fun tipoBancoObjetivo(tipoBanco: String): List<String> = when (tipoBanco.uppercase()) {
                "NV" -> listOf("nv", "nivel", "nivelacion", "nivelación")
                "BL" -> listOf("bl", "blandas", "blanda")
                else -> listOf("pr", "practica", "práctica", "pr")
            }

            fun seleccionarPreguntasBanco(tipoBanco: String, limite: Int): List<ResultRow> {
                if (limite <= 0) return emptyList()

                val seleccionadas = mutableListOf<ResultRow>()
                val usados = mutableSetOf<UUID>()

                fun tomar(tipoPregunta: String?, cantidad: Int): List<ResultRow> {
                    if (cantidad <= 0) return emptyList()

                    val bancosObjetivo = tipoBancoObjetivo(tipoBanco)

                    val query = PreguntaTable
                        .selectAll()
                        .where {
                            (PreguntaTable.tipoBanco.lowerCase() inList bancosObjetivo) and
                            (PreguntaTable.sector eq req.sector) and
                            (PreguntaTable.nivel eq nivelNormalizado) and
                            (PreguntaTable.activa eq true)
                        }

                    tipoPregunta?.let {
                        query.andWhere { PreguntaTable.tipoPregunta eq it }
                    }

                    if (usados.isNotEmpty()) {
                        query.andWhere { PreguntaTable.preguntaId notInList usados.toList() }
                    }

                    val rows = query
                        .orderBy(Random())
                        .limit(cantidad)
                        .toList()

                    usados += rows.map { it[PreguntaTable.preguntaId] }
                    return rows
                }

                if (cuotasTipoPregunta != null) {
                    val (cuotaMulti, cuotaAbiertas) = cuotasPorLimite(limite)

                    val cerradas = tomar("opcion_multiple", min(limite, cuotaMulti ?: limite))
                    val abiertas = tomar("abierta", min(limite - cerradas.size, cuotaAbiertas ?: 0))
                    val restante = limite - cerradas.size - abiertas.size

                    seleccionadas += cerradas + abiertas + tomar(null, restante)
                } else {
                    seleccionadas += tomar(null, limite)
                }

                return seleccionadas
            }

            // 2) Seleccionar preguntas
            val filasPreguntas: List<ResultRow> =
                if (modoPrueba == "MIX" || modoPrueba == "ENT") {
                    // ---------- MODO MIX / ENT (PR + NV + BL) ----------
                    if (usarCustomCantidades) {
                        val nvRows = if (cantNV > 0) seleccionarPreguntasBanco("NV", cantNV) else emptyList()
                        val prRows = if (cantPR > 0) seleccionarPreguntasBanco("PR", cantPR) else emptyList()
                        val blRows = if (cantBL > 0) seleccionarPreguntasBanco("BL", cantBL) else emptyList()

                        (nvRows + prRows + blRows)
                            .shuffled()
                    } else {
                        // Distribución automática (comportamiento antiguo)
                        val maxPorBanco = MAX_PREGUNTAS / 3  // ej: 3 NV, 3 PR, 3 BL (queda 1 libre)

                        val nvRows = seleccionarPreguntasBanco("NV", maxPorBanco)
                        val prRows = seleccionarPreguntasBanco("PR", maxPorBanco)
                        val blRows = seleccionarPreguntasBanco("BL", maxPorBanco)

                        (nvRows + prRows + blRows)
                            .shuffled()
                            .take(MAX_PREGUNTAS)
                    }
                } else {
                    // ---------- MODO PR / NV / BL CLÁSICO ----------
                    // Aquí NO usamos cantidades por banco aunque vengan en el request,
                    // pero sí respetamos las cuotas de tipo de pregunta si vienen.
                    seleccionarPreguntasBanco(modoPrueba, MAX_PREGUNTAS)
                }

            // 3) Insertar en PRUEBA_PREGUNTA y armar DTO
            var orden = 1

            for (row in filasPreguntas) {
                val preguntaId = row[PreguntaTable.preguntaId]
                val tipoBanco = row[PreguntaTable.tipoBanco]
                val sector = row[PreguntaTable.sector]
                val nivel = row[PreguntaTable.nivel]
                val tipoPregunta = row[PreguntaTable.tipoPregunta]
                val texto = row[PreguntaTable.texto]
                val pistasStr = row[PreguntaTable.pistas]
                val configStr = row[PreguntaTable.configRespuesta]
                val configEvalStr = row[PreguntaTable.configEvaluacion]

                val pistasJson: JsonElement? = pistasStr?.let {
                    try {
                        Json.parseToJsonElement(it)
                    } catch (_: Exception) {
                        null
                    }
                }

                val configJson = Json.parseToJsonElement(configStr).jsonObject

                val configEvaluacionJson: JsonElement? = configEvalStr?.let {
                    try {
                        Json.parseToJsonElement(it)
                    } catch (_: Exception) {
                        null
                    }
                }

                val tieneOpciones = configJson["opciones"] != null

                val respuestaCorrecta: String? =
                    if (tieneOpciones)
                        configJson["respuesta_correcta"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.take(40)
                    else null

                PruebaPreguntaTable.insert {
                    it[PruebaPreguntaTable.pruebaId] = pruebaId
                    it[PruebaPreguntaTable.preguntaId] = preguntaId
                    it[PruebaPreguntaTable.orden] = orden
                    it[PruebaPreguntaTable.opciones] = null
                    it[PruebaPreguntaTable.claveCorrecta] = respuestaCorrecta
                }

                // Al front solo le mandamos lo que necesita para dibujar la UI
                val configSinClave = buildJsonObject {
                    configJson["opciones"]?.let { put("opciones", it) }
                    configJson["max_caracteres"]?.let { put("max_caracteres", it) }
                    configJson["min_caracteres"]?.let { put("min_caracteres", it) }
                    // Para preguntas abiertas de blandas
                    configJson["formato"]?.let { put("formato", it) }
                    // Si más adelante agregas otro campo como "tipo", también se puede mandar
                    configJson["tipo"]?.let { put("tipo", it) }
                }

                preguntasSeleccionadas.add(
                    PreguntaPruebaDto(
                        preguntaId = preguntaId.toString(),
                        texto = texto,
                        tipoBanco = tipoBanco,
                        sector = sector,
                        nivel = nivel,
                        tipoPregunta = tipoPregunta,
                        pistas = pistasJson,
                        configRespuesta = configSinClave,
                        configEvaluacion = configEvaluacionJson,
                        orden = orden
                    )
                )

                orden++
            }
        }

        val resp = CrearPruebaNivelacionRes(
            pruebaId = pruebaId.toString(),
            tipoPrueba = etiquetaTipoPrueba,
            area = req.sector,
            nivel = nivelNormalizado,
            metadata = mapOf(
                "metaCargo" to req.metaCargo,
                "usuarioId" to (req.usuarioId ?: ""),
                "nombreUsuario" to (req.nombreUsuario ?: ""),
                "nivelSolicitado" to nivelNormalizado,
                "tipoBanco" to tipoBancoMeta,
                "tipoPrueba" to etiquetaTipoPrueba,
                "esPremium" to esPremium.toString()
            ),
            preguntas = preguntasSeleccionadas
        )

        call.respond(resp)
    }
}
