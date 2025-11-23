package data.tables.jobs

import org.jetbrains.exposed.sql.Table

/**
 * Tabla que guarda los requisitos consolidados por cargo.
 *
 * CREATE TABLE job_requisito (
 *   job_requisito_id UUID PRIMARY KEY,
 *   area             VARCHAR(80),
 *   cargo            VARCHAR(120) NOT NULL,
 *   nivel_inferido   VARCHAR(10)  NOT NULL,
 *   tipo             VARCHAR(20)  NOT NULL, -- "tecnico" / "blando"
 *   texto            TEXT         NOT NULL,
 *   fuente_titulo    VARCHAR(255),
 *   empresa          VARCHAR(255),
 *   ubicacion        VARCHAR(255),
 *   url_aviso        VARCHAR(512)
 * );
 */
object JobRequisitoTable : Table("job_requisito") {

    val id = uuid("job_requisito_id")
    val area = varchar("area", length = 80).nullable()
    val cargo = varchar("cargo", length = 120)
    val nivelInferido = varchar("nivel_inferido", length = 10)
    val tipo = varchar("tipo", length = 20)          // "tecnico" / "blando"
    val texto = text("texto")
    val fuenteTitulo = varchar("fuente_titulo", length = 255).nullable()
    val empresa = varchar("empresa", length = 255).nullable()
    val ubicacion = varchar("ubicacion", length = 255).nullable()
    val urlAviso = varchar("url_aviso", length = 512).nullable()

    override val primaryKey = PrimaryKey(id)
}
