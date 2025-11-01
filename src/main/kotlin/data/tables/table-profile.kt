package data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

// Tabla real: app.perfil_usuario  (usa ?currentSchema=app en tu DB_URL)
object ProfileTable : Table("perfil_usuario") {
    val perfilId           = uuid("perfil_id")
    val usuarioId          = uuid("usuario_id").index()
    val nivelExperiencia   = varchar("nivel_experiencia", 40).nullable()
    val area               = varchar("area", 10).nullable()
    val flagsAccTxt        = text("flags_accesibilidad").nullable()   // <<— JSON como String
    val notaObjetivos      = text("nota_objetivos").nullable()
    val pais               = varchar("pais", 2).nullable()
    val fechaActualizacion = timestampWithTimeZone("fecha_actualizacion")

    override val primaryKey = PrimaryKey(perfilId)
}
