package data.tables.sesiones

import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Tabla de sesiones de entrevista tipo chat.
 * Cada sesión representa una práctica interactiva con preguntas y respuestas.
 */
object SesionEntrevistaTable : Table("app.sesion_entrevista") {
    val sesionId = uuid("sesion_id")
    val usuarioId = uuid("usuario_id").references(UsuarioTable.usuarioId)
    val modo = varchar("modo", 5)              // tec, soft, mix
    val nivel = varchar("nivel", 3)            // jr, mid, sr
    val fechaInicio = timestampWithTimeZone("fecha_inicio")
    val fechaFin = timestampWithTimeZone("fecha_fin").nullable()
    val esPremium = bool("es_premium").default(false)
    val puntajeGeneral = decimal("puntaje_general", 5, 2).nullable()

    override val primaryKey = PrimaryKey(sesionId)
}
