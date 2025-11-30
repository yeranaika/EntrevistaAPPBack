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
import java.util.UUID

// =============================
//  Tablas Exposed (locales)
// =============================

object PruebaTable : Table("prueba") {
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }

    // Aumentamos a 16 para tener holgura; valor usado: "practica" (8 chars)
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
    val tipoBanco = varchar("tipo_banco", 5)
    val sector = varchar("sector", 80)
    val nivel = varchar("nivel", 3)            // jr | mid | sr

    // tipo_pregunta en BD: por ejemplo "alternativa", "abierta", etc.
    // Ajusta el largo si en tu esquema es distinto
    val tipoPregunta = varchar("tipo_pregunta", 20)

    val texto = text("texto")
    val pistas = text("pistas").nullable()          // jsonb en BD, se lee como String
    val configRespuesta = text("config_respuesta")  // jsonb en BD, se lee como String
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
    val metaCargo: String
)

@Serializable
data class PreguntaPruebaDto(
    val preguntaId: String,
    val texto: String,
    val tipoBanco: String,
    val sector: String,
    val nivel: String,
    val tipoPregunta: String,          // <-- NUEVO CAMPO EN LA RESPUESTA
    val pistas: JsonElement? = null,
    val configRespuesta: JsonElement,
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
 * Crea una prueba de práctica usando preguntas del banco:
 *   - tipo_banco = 'PR'   (preguntas técnicas base)
 *   - sector + nivel      (filtrado)
 * Máximo 10 preguntas, aleatorias.
 */
fun Route.pruebaFrontRoutes() {

    post("/api/prueba-practica/front") {
        val req = call.receive<CrearPruebaNivelacionReq>()

        val nivelNormalizado = req.nivel.trim().lowercase()
        val nivelesValidos = setOf("jr", "mid", "sr")

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

        lateinit var pruebaId: UUID
        val preguntasSeleccionadas = mutableListOf<PreguntaPruebaDto>()

        transaction {
            // 1) Crear fila en PRUEBA

            // área (sector) limitado a 80 chars por la columna
            val areaSafe = req.sector.take(80)

            // metaCargo lo limitamos a 80 solo para meterlo en metadata (si quieres)
            val metaCargoSafe = req.metaCargo.take(80)

            val metadataJson = buildJsonObject {
                put("metaCargo", JsonPrimitive(metaCargoSafe))
                put("nivelSolicitado", JsonPrimitive(nivelNormalizado))
                put("usuarioId", JsonPrimitive(req.usuarioId ?: ""))
                put("nombreUsuario", JsonPrimitive(req.nombreUsuario ?: ""))
            }.toString()

            pruebaId = PruebaTable.insert {
                // valor consistente con longitud (<=16)
                it[tipoPrueba] = "practica"
                it[area] = areaSafe
                it[nivel] = nivelNormalizado
                it[metadata] = metadataJson   // TEXT, sin límite desde Exposed
                it[activo] = true
            } get PruebaTable.pruebaId

            // 2) Seleccionar preguntas activas de tipo PR (banco original, NO IAJOB)
            val filasPreguntas = PreguntaTable
                .selectAll()
                .where {
                    (PreguntaTable.tipoBanco eq "PR") and
                    (PreguntaTable.sector eq req.sector) and
                    (PreguntaTable.nivel eq nivelNormalizado) and
                    (PreguntaTable.activa eq true)
                }
                .orderBy(Random())      // Exposed Random()
                .limit(MAX_PREGUNTAS)
                .toList()

            var orden = 1

            for (row in filasPreguntas) {
                val preguntaId = row[PreguntaTable.preguntaId]
                val tipoBanco = row[PreguntaTable.tipoBanco]
                val sector = row[PreguntaTable.sector]
                val nivel = row[PreguntaTable.nivel]
                val tipoPregunta = row[PreguntaTable.tipoPregunta]  // <-- leemos tipo_pregunta
                val texto = row[PreguntaTable.texto]
                val pistasStr = row[PreguntaTable.pistas]
                val configStr = row[PreguntaTable.configRespuesta]

                val pistasJson: JsonElement? = pistasStr?.let {
                    try {
                        Json.parseToJsonElement(it)
                    } catch (_: Exception) {
                        null
                    }
                }

                val configJson = Json.parseToJsonElement(configStr).jsonObject

                // Nuevo modelo: NO usamos "tipo" en el JSON.
                // Inferimos si es cerrada mirando si tiene "opciones".
                val tieneOpciones = configJson["opciones"] != null

                // Truncamos a 40 chars para respetar la columna clave_correcta (VARCHAR(40))
                val respuestaCorrecta: String? =
                    if (tieneOpciones)
                        configJson["respuesta_correcta"]
                            ?.jsonPrimitive
                            ?.contentOrNull
                            ?.take(40)
                    else null

                // 3) Guardar relación en PRUEBA_PREGUNTA
                PruebaPreguntaTable.insert {
                    it[PruebaPreguntaTable.pruebaId] = pruebaId
                    it[PruebaPreguntaTable.preguntaId] = preguntaId
                    it[PruebaPreguntaTable.orden] = orden
                    it[PruebaPreguntaTable.opciones] = null     // por ahora no la usamos
                    it[PruebaPreguntaTable.claveCorrecta] = respuestaCorrecta
                }

                // Configuración SIN 'respuesta_correcta' para mandar al front
                val configSinClave = buildJsonObject {
                    configJson["opciones"]?.let { put("opciones", it) }
                    configJson["max_caracteres"]?.let { put("max_caracteres", it) }
                    configJson["min_caracteres"]?.let { put("min_caracteres", it) }
                }

                preguntasSeleccionadas.add(
                    PreguntaPruebaDto(
                        preguntaId = preguntaId.toString(),
                        texto = texto,
                        tipoBanco = tipoBanco,
                        sector = sector,
                        nivel = nivel,
                        tipoPregunta = tipoPregunta,   // <-- se expone al front
                        pistas = pistasJson,
                        configRespuesta = configSinClave,
                        orden = orden
                    )
                )

                orden++
            }
        }

        val resp = CrearPruebaNivelacionRes(
            pruebaId = pruebaId.toString(),
            tipoPrueba = "practica",        // etiqueta para el front
            area = req.sector,
            nivel = nivelNormalizado,
            metadata = mapOf(
                "metaCargo" to req.metaCargo,
                "usuarioId" to (req.usuarioId ?: ""),
                "nombreUsuario" to (req.nombreUsuario ?: ""),
                "nivelSolicitado" to nivelNormalizado
            ),
            preguntas = preguntasSeleccionadas
        )

        call.respond(resp)
    }
}
