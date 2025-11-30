package data.tables.cuestionario.preguntas

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.Column
import java.util.UUID

// Custom column type for PostgreSQL JSON
class JsonColumnType : TextColumnType() {
    override fun sqlType(): String = "JSON"
}

fun Table.jsonText(name: String): Column<String> = registerColumn(name, JsonColumnType())

object PreguntaTable : Table("app.pregunta") {
    val preguntaId   = uuid("pregunta_id")                  // PK UUID "a mano"
    // Alias para compatibilidad con el resto del c�digo
    val id           = preguntaId
    val tipoBanco    = varchar("tipo_banco", 5)
    val nivel        = varchar("nivel", 3)
    val sector       = varchar("sector", 80).nullable()
    val texto        = text("texto")
    val pistas       = jsonText("pistas").nullable()        // JSON column
    val historica    = jsonText("historica").nullable()     // JSON column
    val activa       = bool("activa").default(true)
    val fechaCreacion= timestampWithTimeZone("fecha_creacion")

    override val primaryKey = PrimaryKey(id)
}
