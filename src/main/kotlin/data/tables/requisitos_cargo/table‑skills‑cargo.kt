package data.tables.requisitos_cargo

import org.jetbrains.exposed.sql.Table
import java.util.UUID

/**
 * Tabla Exposed para la tabla skills_cargo.
 */
object SkillsCargoTable : Table(name = "skills_cargo") {
    val id          = uuid("id").clientDefault { UUID.randomUUID() }
    val cargo       = varchar("cargo", 120)
    val tipo        = varchar("tipo", 10)
    val descripcion = text("descripcion")

    override val primaryKey = PrimaryKey(id)
}
