package tables.cuestionario.prueba

import org.jetbrains.exposed.sql.Table

object PruebaTable : Table("prueba") {
    val id = uuid("prueba_id")
    val tipoPrueba = varchar("tipo_prueba", 8).default("aprendiz")
    val area = varchar("area", 5).nullable()  // Para almacenar enum: tec, soft, mix
    val nivel = varchar("nivel", 3).nullable()  // Para almacenar enum: jr, mid, sr
    val metadata = text("metadata").nullable()  // TEXT para almacenar JSON
    val historica = text("historica").nullable()  // TEXT para almacenar JSON de cambios históricos
    val activo = bool("activo").default(true)

    override val primaryKey = PrimaryKey(id)
}
