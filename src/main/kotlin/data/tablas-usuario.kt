package data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant
import java.util.UUID

object UsuarioTable : Table("usuario") {
    val usuarioId = uuid("usuario_id").clientDefault { UUID.randomUUID() }
    val correo = varchar("correo", 320).uniqueIndex()
    val contrasenaHash = varchar("contrasena_hash", 255)
    val nombre = varchar("nombre", 120).nullable()
    val idioma = varchar("idioma", 10).default("es")
    val estado = varchar("estado", 19).default("activo")
    val fechaCreacion = timestamp("fecha_creacion").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(usuarioId)
}

object PerfilUsuarioTable : Table("perfil_usuario") {
    val perfilId = uuid("perfil_id").clientDefault { UUID.randomUUID() }
    val usuarioId = reference("usuario_id", UsuarioTable.usuarioId, onDelete = ReferenceOption.CASCADE)
    val nivelExperiencia = varchar("nivel_experiencia", 40).nullable()
    val area = varchar("area", 10).nullable()
    // ✅ Aquí el cambio: pasamos una instancia de Json, no un serializer
    val flagsAccesibilidad = jsonb<JsonElement>("flags_accesibilidad", Json).nullable()
    val notaObjetivos = text("nota_objetivos").nullable()
    val pais = varchar("pais", 2).nullable()
    val fechaActualizacion = timestamp("fecha_actualizacion").clientDefault { Instant.now() }
    override val primaryKey = PrimaryKey(perfilId)
}
