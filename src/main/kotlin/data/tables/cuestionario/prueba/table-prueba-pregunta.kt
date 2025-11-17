package tables.cuestionario.prueba

import org.jetbrains.exposed.sql.Table

object PruebaPreguntaTable : Table("app.prueba_pregunta") {
    val id = uuid("prueba_pregunta_id")
    val pruebaId = uuid("prueba_id")
    val preguntaId = uuid("pregunta_id")
    val orden = integer("orden")
    val opciones = text("opciones").nullable()
    val claveCorrecta = varchar("clave_correcta", 40).nullable()

    override val primaryKey = PrimaryKey(id)
}
