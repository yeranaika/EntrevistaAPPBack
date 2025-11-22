package data.tables.sesiones

import org.jetbrains.exposed.sql.Table

/**
 * Tabla de retroalimentación (feedback) generado para respuestas.
 * Contiene el análisis automático de la calidad de la respuesta.
 *
 * Nota: aciertos y faltantes se almacenan como TEXT (JSON serializado como string)
 * para evitar problemas de compatibilidad con el tipo JSON de PostgreSQL.
 */
object RetroalimentacionTable : Table("app.retroalimentacion") {
    val retroalimentacionId = uuid("retroalimentacion_id")
    val respuestaId = uuid("respuesta_id").references(RespuestaTable.respuestaId).uniqueIndex()
    val nivelFeedback = varchar("nivel_feedback", 8)
    val enunciado = text("enunciado").nullable()
    val aciertos = text("aciertos").nullable()  // JSON serializado como TEXT
    val faltantes = text("faltantes").nullable()  // JSON serializado como TEXT

    override val primaryKey = PrimaryKey(retroalimentacionId)
}
