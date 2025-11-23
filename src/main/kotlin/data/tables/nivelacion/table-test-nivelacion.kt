package data.tables.nivelacion

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object PreguntaNivelacionTable : UUIDTable("app.pregunta", "pregunta_id") {
    val tipoBanco = varchar("tipo_banco", 5).nullable()       // 'NV' = nivelación
    val sector = varchar("sector", 80).nullable()             // área
    val nivel = varchar("nivel", 3).nullable()                // 'jr','ssr','sr'
    val metaCargo = varchar("meta_cargo", 120).nullable()     // cargo objetivo (si lo agregaste a la BD)
    val tipoPregunta = varchar("tipo_pregunta", 20)           // 'opcion_multiple' / 'abierta', etc.

    val texto = text("texto")
    val pistas = text("pistas").nullable()
    val configRespuesta = text("config_respuesta").nullable()
    val activa = bool("activa").default(true)
    val fechaCreacion = timestampWithTimeZone("fecha_creacion")
}
