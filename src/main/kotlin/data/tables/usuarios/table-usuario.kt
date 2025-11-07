package data.tables.usuarios

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UsuarioTable : Table("usuario") {

    val usuarioId      = uuid("usuario_id")
    val correo         = varchar("correo", 320).uniqueIndex("usuario_correo_unique")
    val contrasenaHash = varchar("contrasena_hash", 255)
    val nombre         = varchar("nombre", 120).nullable()
    val idioma         = varchar("idioma", 10).default("es")
    val estado         = varchar("estado", 19).default("activo")
    val rol            = varchar("rol", 10).default("user")

    // Default en app (no depende de la BD)
    val fechaCreacion  = datetime("fecha_creacion")
        .clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(usuarioId, name = "usuario_pk")
}
