// data/tables/cuestionario/preguntas/table-pregunta.kt
package data.tables.cuestionario.preguntas

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object PreguntaTable : Table("pregunta") {
    val id = uuid("pregunta_id")
    val tipoBanco = varchar("tipo_banco", 5)
    val sector = varchar("sector", 80)
    val nivel = varchar("nivel", 3)
    val metaCargo = varchar("meta_cargo", 120).nullable()
    val tipoPregunta = varchar("tipo_pregunta", 20)
    val texto = text("texto")

    // JSONB en BD, pero aquí lo manejamos como TEXT
    val pistas = text("pistas").nullable()
    val configRespuesta = text("config_respuesta")
    val configEvaluacion = text("config_evaluacion").nullable()

    val activa = bool("activa").default(true)
    val fechaCreacion = timestamp("fecha_creacion")  // Instant

    override val primaryKey = PrimaryKey(id)
}
