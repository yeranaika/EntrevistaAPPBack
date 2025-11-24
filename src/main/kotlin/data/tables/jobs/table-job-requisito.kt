package data.tables.jobs

import org.jetbrains.exposed.sql.Table
import java.util.UUID

object JobRequisitoTable : Table("job_requisito") {
    val id = uuid("job_requisito_id").clientDefault { UUID.randomUUID() }
    val area = varchar("area", length = 80).nullable()
    val cargo = varchar("cargo", length = 120)
    val nivelInferido = varchar("nivel_inferido", length = 10).nullable()
    val tipo = varchar("tipo", length = 20)      // "tecnico" | "blando"
    val texto = text("texto")
    val fuenteTitulo = text("fuente_titulo").nullable()
    val empresa = varchar("empresa", 200).nullable()
    val ubicacion = varchar("ubicacion", 200).nullable()
    val urlAviso = text("url_aviso").nullable()
    // si ya usas un tipo de fecha distinto, copia el que uses en otras tablas
    // val creadoEn = datetime("creado_en")
    override val primaryKey = PrimaryKey(id)
}
