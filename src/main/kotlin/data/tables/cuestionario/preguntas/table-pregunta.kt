// tables/cuestionario/preguntas/PreguntaTable.kt
package tables.cuestionario.preguntas

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID

object PreguntaTable : Table("pregunta") {
    val id           = uuid("pregunta_id")                  // PK UUID “a mano”
    val tipoBanco    = varchar("tipo_banco", 5)
    val nivel        = varchar("nivel", 3)
    val sector       = varchar("sector", 80).nullable()
    val texto        = text("texto")
    val pistas       = text("pistas").nullable()            // guardas JSON como TEXT
    val historica    = text("historica").nullable()         // idem
    val activa       = bool("activa").default(true)
    val fechaCreacion= timestampWithTimeZone("fecha_creacion")

    override val primaryKey = PrimaryKey(id)
}
