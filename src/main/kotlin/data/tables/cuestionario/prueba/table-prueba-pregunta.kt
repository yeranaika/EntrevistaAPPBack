// data/tables/cuestionario/prueba/table-prueba-pregunta.kt
package data.tables.cuestionario.prueba

import data.tables.cuestionario.preguntas.PreguntaTable
import org.jetbrains.exposed.sql.Table
import java.util.UUID

object PruebaPreguntaTable : Table("prueba_pregunta") {
    val pruebaPreguntaId = uuid("prueba_pregunta_id").clientDefault { UUID.randomUUID() }
    val id = pruebaPreguntaId   // alias

    val pruebaId   = uuid("prueba_id").references(PruebaTable.pruebaId)
    val preguntaId = uuid("pregunta_id").references(PreguntaTable.id)

    val orden         = integer("orden")
    val opciones      = text("opciones").nullable()          // JSON como String
    val claveCorrecta = varchar("clave_correcta", 100).nullable()

    override val primaryKey = PrimaryKey(pruebaPreguntaId)
}
