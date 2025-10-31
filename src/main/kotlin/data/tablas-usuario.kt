package data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UsuarioTable : Table("usuario") {
    val usuarioId = uuid("usuario_id")
    val correo = varchar("correo", 320).uniqueIndex()
    val contrasenaHash = varchar("contrasena_hash", 255)
    val nombre = varchar("nombre", 120).nullable()
    val idioma = varchar("idioma", 10).default("es")
    val estado = varchar("estado", 19).default("activo")
    val fechaCreacion = timestamp("fecha_creacion").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(usuarioId)
}
