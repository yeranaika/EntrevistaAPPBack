package data.tables.usuarios

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object ObjetivoCarreraTable : UUIDTable("objetivo_carrera", "objetivo_id") {

    // Usamos la columna usuario_id de UsuarioTable
    val usuarioId = reference(
        name = "usuario_id",
        refColumn = UsuarioTable.usuarioId,
        onDelete = ReferenceOption.CASCADE
    )

    val nombreCargo = varchar("nombre_cargo", length = 120)
    val sector = varchar("sector", length = 10).nullable()
    val activo = bool("activo").default(true)
}
