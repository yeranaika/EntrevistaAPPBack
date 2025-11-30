package data.tables.cuestionario.prueba

import org.jetbrains.exposed.sql.Table
import java.util.UUID

object PruebaTable : Table("prueba") {

    // PK
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }
    // Alias para que el código que usa PruebaTable.id funcione
    val id = pruebaId

    val tipoPrueba = varchar("tipo_prueba", 20)
    val area = varchar("area", 80).nullable()
    val nivel = varchar("nivel", 20).nullable()

    // En tu DB puede ser JSONB, aquí lo guardamos como texto
    val metadata = text("metadata").nullable()
    val historica = text("historica").nullable()

    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(pruebaId)
}
