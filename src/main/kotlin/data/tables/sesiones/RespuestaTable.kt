package data.tables.sesiones

import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Tabla de respuestas del usuario a preguntas de sesiones de entrevista.
 */
object RespuestaTable : Table("app.respuesta") {
    val respuestaId = uuid("respuesta_id")
    val sesionPreguntaId = uuid("sesion_pregunta_id").references(SesionPreguntaTable.sesionPreguntaId)
    val usuarioId = uuid("usuario_id").references(UsuarioTable.usuarioId)
    val texto = text("texto")
    val fechaCreacion = timestampWithTimeZone("fecha_creacion")
    val tokensIn = integer("tokens_in").nullable()

    override val primaryKey = PrimaryKey(respuestaId)
}
