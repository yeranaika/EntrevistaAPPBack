package data.tables.nivelacion

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Vista de la tabla genérica app.pregunta,
 * usada para preguntas de nivelación (tipo_banco = 'NV')
 */
object PreguntaNivelacionTable : UUIDTable("app.pregunta", "pregunta_id") {

    val tipoBanco = varchar("tipo_banco", 5).nullable()       // 'NV' = nivelación
    val sector = varchar("sector", 80).nullable()             // área
    val nivel = varchar("nivel", 3).nullable()                // 'jr','ssr','sr'
    val metaCargo = varchar("meta_cargo", 120).nullable()     // cargo objetivo

    // 'opcion_multiple' o 'abierta'
    val tipoPregunta = varchar("tipo_pregunta", 20).default("opcion_multiple")

    val texto = text("texto")                                 // enunciado
    val pistas = text("pistas").nullable()                    // JSONB → String
    val configRespuesta = text("config_respuesta").nullable() // JSONB → String

    val activa = bool("activa").default(true)
    val fechaCreacion = timestampWithTimeZone("fecha_creacion")
}
