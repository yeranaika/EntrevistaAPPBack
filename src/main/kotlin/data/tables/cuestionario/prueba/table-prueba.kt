// data/tables/cuestionario/prueba/table-prueba.kt
package data.tables.cuestionario.prueba

import org.jetbrains.exposed.sql.Table
import java.util.UUID

object PruebaTable : Table("prueba") {
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }
    val id = pruebaId   // alias

    // DB column is VARCHAR(8): values like "practica", "nivel", "blended"
    val tipoPrueba = varchar("tipo_prueba", 8)
    val area       = varchar("area", 80).nullable()
    val nivel      = varchar("nivel", 3).nullable()
    val metadata   = text("metadata").nullable()
    val activo     = bool("activo").default(true)

    override val primaryKey = PrimaryKey(pruebaId)
}
