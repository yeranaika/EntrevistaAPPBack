package tables.cuestionario.preguntas

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object PreguntaTable : UUIDTable(name = "app.pregunta", columnName = "pregunta_id") {
    val tipoBanco = varchar("tipo_banco", 5)            // tec|soft|mix
    val sector = varchar("sector", 80).nullable()
    val nivel = varchar("nivel", 3)                     // jr|mid|sr
    val texto = text("texto")
    // En BD son JSON; aquí los guardamos como TEXT con JSON string
    val pistas = text("pistas").nullable()
    val historica = text("historica").nullable()
    val activa = bool("activa").default(true)
    val fechaCreacion = timestamp("fecha_creacion")     // DEFAULT now() lo da Postgres
}
