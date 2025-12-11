package routes.cuestionario.prueba_practica

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
import java.util.UUID

// =============================
//  Tablas Exposed (locales)
// =============================

object PruebaTable : Table("prueba") {
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }

    // En BD es VARCHAR(8) -> valores: "practica", "nivel", "blended"
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
    val tipoBanco = varchar("tipo_banco", 5)      // PR / NV
    val sector = varchar("sector", 80)
    val nivel = varchar("nivel", 3)               // jr | mid | sr | "1","2","3" en NV

    // tipo_pregunta en BD: "opcion_multiple" | "abierta"
    val tipoPregunta = varchar("tipo_pregunta", 20)

    val texto = text("texto")

    // jsonb en BD, las manejamos como String
    val pistas = text("pistas").nullable()
    val configRespuesta = text("config_respuesta")
    val configEvaluacion = text("config_evaluacion").nullable()   // <--- NUEVA COLUMNA

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
    // Ahora puede ser: "PR", "NV" o "BL" (blended: mezcla NV + PR)
    val tipoPrueba: String? = null
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
    // opcional: mandamos la meta de evaluación (NLP + STAR) si la quieres usar en el front o para debug
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
 *   - tipo_banco = 'PR' o 'NV' (modo clásico)
 *   - tipo_banco MIX (NV + PR) cuando tipoPrueba = 'BL' (blended)
 *
 * Máximo 10 preguntas, aleatorias.
 */
fun Route.pruebaFrontRoutes() {

    post("/api/prueba-practica/front") {
        val req = call.receive<CrearPruebaNivelacionReq>()

        val nivelNormalizado = req.nivel.trim().lowercase()
        val nivelesValidos = setOf("jr", "mid", "sr")

        // =========================
        // Validar tipoPrueba: PR/NV/BL
        // =========================
        val tipoBancoSolicitado = req.tipoPrueba
            ?.trim()
            ?.uppercase()

        // Aceptamos también BL para blended (mezcla NV + PR)
        val tiposBancoValidos = setOf("PR", "NV", "BL")

        if (tipoBancoSolicitado == null) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "tipoPrueba es obligatorio y debe ser PR (práctica), NV (nivelación) o BL (blended)"
                )
            )
        }

        if (tipoBancoSolicitado !in tiposBancoValidos) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to "tipoPrueba inválido. Debe ser PR (práctica), NV (nivelación) o BL (blended)"
                )
            )
        }

        // Lo que se guarda en la columna tipo_prueba (VARCHAR(8) en BD)
        val etiquetaTipoPrueba = when (tipoBancoSolicitado) {
            "NV"  -> "nivel"     // 5 caracteres
            "PR"  -> "practica"  // 8 caracteres
            "BL"  -> "blended"   // 7 caracteres -> cabe en varchar(8)
            else  -> "practica"
        }

        if (nivelNormalizado !in nivelesValidos) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "nivel debe ser uno de: jr, mid, sr")
            )
        }

        if (req.sector.isBlank() || req.metaCargo.isBlank()) {
            return@post call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "sector y metaCargo son obligatorios")
            )
        }

        val MAX_PREGUNTAS = 10

        // Para metadata y respuesta: cuando es BL lo marcamos como MIX
        val tipoBancoMeta = if (tipoBancoSolicitado == "BL") "MIX" else tipoBancoSolicitado

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
                put("nombreUsuario", JsonPrimitive(req.nombreUsuario ?: ""))
            }.toString()

            pruebaId = PruebaTable.insert {
                it[tipoPrueba] = etiquetaTipoPrueba
                it[area] = areaSafe
                it[nivel] = nivelNormalizado
                it[metadata] = metadataJson
                it[activo] = true
            } get PruebaTable.pruebaId

            // 2) Seleccionar preguntas
            val filasPreguntas: List<ResultRow> =
                if (tipoBancoSolicitado == "BL") {
                    // ---------- MODO BLENDED (NV + PR) ----------
                    val maxPorTipo = MAX_PREGUNTAS / 2  // p.ej. 5 de NV y 5 de PR

                    // Preguntas de nivelación (NV)
                    val nivelacionRows = PreguntaTable
                        .selectAll()
                        .where {
                            (PreguntaTable.tipoBanco eq "NV") and
                            (PreguntaTable.sector eq req.sector) and
                            (PreguntaTable.nivel eq nivelNormalizado) and
                            (PreguntaTable.activa eq true)
                        }
                        .orderBy(Random())
                        .limit(maxPorTipo)
                        .toList()

                    // Preguntas de práctica técnica (PR)
                    val practicaRows = PreguntaTable
                        .selectAll()
                        .where {
                            (PreguntaTable.tipoBanco eq "PR") and
                            (PreguntaTable.sector eq req.sector) and
                            (PreguntaTable.nivel eq nivelNormalizado) and
                            (PreguntaTable.activa eq true)
                        }
                        .orderBy(Random())
                        .limit(maxPorTipo)
                        .toList()

                    // Si luego agregas soft skills, las sumas aquí:
                    // val softRows = ...

                    (nivelacionRows + practicaRows)
                        .shuffled()
                        .take(MAX_PREGUNTAS)
                } else {
                    // ---------- MODO PR / NV CLÁSICO ----------
                    PreguntaTable
                        .selectAll()
                        .where {
                            (PreguntaTable.tipoBanco eq tipoBancoSolicitado) and
                            (PreguntaTable.sector eq req.sector) and
                            (PreguntaTable.nivel eq nivelNormalizado) and
                            (PreguntaTable.activa eq true)
                        }
                        .orderBy(Random())
                        .limit(MAX_PREGUNTAS)
                        .toList()
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
                        configEvaluacion = configEvaluacionJson,   // <- meta NLP + STAR (puedes ignorarla en Android si no la usas aún)
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
                "tipoPrueba" to etiquetaTipoPrueba
            ),
            preguntas = preguntasSeleccionadas
        )

        call.respond(resp)
    }
}
