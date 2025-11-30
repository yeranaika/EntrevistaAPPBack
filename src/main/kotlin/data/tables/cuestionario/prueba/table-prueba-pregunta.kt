package data.tables.cuestionario.prueba

import data.tables.cuestionario.preguntas.PreguntaTable
import org.jetbrains.exposed.sql.Table
import java.util.UUID

object PruebaPreguntaTable : Table("prueba_pregunta") {

    // La columna real en la BD es prueba_pregunta_id
    val pruebaPreguntaId = uuid("prueba_pregunta_id").clientDefault { UUID.randomUUID() }
    // Alias para compatibilidad con el resto del c�digo
    val id = pruebaPreguntaId

    val pruebaId = uuid("prueba_id").references(PruebaTable.pruebaId)
    val preguntaId = uuid("pregunta_id").references(PreguntaTable.preguntaId)

    val orden = integer("orden")
    val opciones = text("opciones").nullable()          // JSON de opciones, como texto
    val claveCorrecta = varchar("clave_correcta", 100).nullable()

    override val primaryKey = PrimaryKey(pruebaPreguntaId)
}
