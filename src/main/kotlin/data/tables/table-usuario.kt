package data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

// Si tu conexión usa ?currentSchema=app o search_path=app, basta con "usuario"
object UsuarioTable : Table("usuario") {
    val usuarioId      = uuid("usuario_id")
    val correo         = varchar("correo", 320).uniqueIndex()
    val contrasenaHash = varchar("contrasena_hash", 255)
    val nombre         = varchar("nombre", 120).nullable()
    val idioma         = varchar("idioma", 10).default("es")
    val estado         = varchar("estado", 19).default("activo")
    val fechaCreacion  = timestampWithTimeZone("fecha_creacion") // DEFAULT now()

    override val primaryKey = PrimaryKey(usuarioId)
}
