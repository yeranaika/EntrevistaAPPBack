package routes.cuestionario.prueba

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.HttpStatusCode
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
    val tipoPrueba = varchar("tipo_prueba", 8)
    val area = varchar("area", 80).nullable()
    val nivel = varchar("nivel", 3).nullable()
    val metadata = varchar("metadata", 120).nullable()
    val activo = bool("activo")
    override val primaryKey = PrimaryKey(pruebaId)
}

object PreguntaTable : Table("pregunta") {
    val preguntaId = uuid("pregunta_id")
    val tipoBanco = varchar("tipo_banco", 5)
    val sector = varchar("sector", 80)
    val nivel = varchar("nivel", 4)
    val texto = text("texto")
    val pistas = text("pistas").nullable()           // jsonb en BD, lo leemos como String
    val configRespuesta = text("config_respuesta")   // jsonb en BD, lo leemos como String
    val activa = bool("activa")
    override val primaryKey = PrimaryKey(preguntaId)
}

object PruebaPreguntaTable : Table("prueba_pregunta") {
    val pruebaPreguntaId = uuid("prueba_pregunta_id").clientDefault { UUID.randomUUID() }
    val pruebaId = uuid("prueba_id").references(PruebaTable.pruebaId, onDelete = ReferenceOption.CASCADE)
    val preguntaId = uuid("pregunta_id").references(PreguntaTable.preguntaId, onDelete = ReferenceOption.RESTRICT)
    val orden = integer("orden")
    val opciones = text("opciones").nullable()       // lo dejamos aunque no lo usemos
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
 * POST /api/nivelacion/prueba/front
 *
 * - Crea una prueba de nivelación
 * - Máximo 10 preguntas
 * - Filtra por sector + nivel (jr|mid|sr)
 */
fun Route.pruebaFrontRoutes() {

    post("/api/nivelacion/prueba/front") {
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
            // 1) Crear PRUEBA
        val metaCargoSafe = req.metaCargo.take(80)  // limitamos metaCargo para que no rompa el largo total

        val metadataJson = buildJsonObject {
            put("metaCargo", JsonPrimitive(metaCargoSafe))
            put("nivelSolicitado", JsonPrimitive(nivelNormalizado))
        }.toString()

            pruebaId = PruebaTable.insert {
                // 'tipo_prueba' es VARCHAR(8) → usamos 'aprendiz' (8 letras) o el valor que ya usas en el sistema
                it[tipoPrueba] = "aprendiz"
                it[area] = req.sector
                it[nivel] = nivelNormalizado
                it[metadata] = metadataJson
                it[activo] = true
            } get PruebaTable.pruebaId

            // 2) Seleccionar preguntas desde PREGUNTA
            val filasPreguntas = PreguntaTable
                .selectAll()
                .where {
                    (PreguntaTable.tipoBanco eq "PR") and
                    (PreguntaTable.sector eq req.sector) and
                    (PreguntaTable.nivel eq nivelNormalizado) and
                    (PreguntaTable.activa eq true)
                }
                .orderBy(Random())      // orden aleatorio
                .limit(MAX_PREGUNTAS)
                .toList()

            var orden = 1

            for (row in filasPreguntas) {
                val preguntaId = row[PreguntaTable.preguntaId]
                val tipoBanco = row[PreguntaTable.tipoBanco]
                val sector = row[PreguntaTable.sector]
                val nivel = row[PreguntaTable.nivel]
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

                val tipo = configJson["tipo"]?.jsonPrimitive?.contentOrNull
                val opciones = configJson["opciones"]
                val respuestaCorrecta =
                    if (tipo == "seleccion_unica")
                        configJson["respuesta_correcta"]?.jsonPrimitive?.contentOrNull
                    else null

                // 3) Insertar en PRUEBA_PREGUNTA
                PruebaPreguntaTable.insert {
                    it[PruebaPreguntaTable.pruebaId] = pruebaId
                    it[PruebaPreguntaTable.preguntaId] = preguntaId
                    it[PruebaPreguntaTable.orden] = orden
                   
                    it[PruebaPreguntaTable.claveCorrecta] = respuestaCorrecta
                }

                // Configuración SIN respuesta_correcta para el FRONT
                val configSinClave = buildJsonObject {
                    configJson["tipo"]?.let { put("tipo", it) }
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
            tipoPrueba = "nivelacion",
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
