// data/tables/cuestionario/prueba/table-prueba.kt
package data.tables.cuestionario.prueba

import org.jetbrains.exposed.sql.Table
import java.util.UUID

object PruebaTable : Table("prueba") {
    val pruebaId = uuid("prueba_id").clientDefault { UUID.randomUUID() }
    val id = pruebaId   // alias

    val tipoPrueba = varchar("tipo_prueba", 20)   // 'practica','nivel','simulacion'
    val area       = varchar("area", 80).nullable()
    val nivel      = varchar("nivel", 20).nullable()
    val metadata   = text("metadata").nullable()
    val historica  = text("historica").nullable()
    val activo     = bool("activo").default(true)

    override val primaryKey = PrimaryKey(pruebaId)
}
