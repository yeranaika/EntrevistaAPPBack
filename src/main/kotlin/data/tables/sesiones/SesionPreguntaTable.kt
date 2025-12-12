package data.tables.sesiones

import data.tables.cuestionario.preguntas.PreguntaTable
import org.jetbrains.exposed.sql.Table

/**
 * Tabla que asocia preguntas específicas a una sesión de entrevista.
 * Controla el orden de presentación y tracking de preguntas ya mostradas.
 */
object SesionPreguntaTable : Table("app.sesion_pregunta") {
    val sesionPreguntaId = uuid("sesion_pregunta_id")
    val sesionId = uuid("sesion_id").references(SesionEntrevistaTable.sesionId)
    val preguntaId = uuid("pregunta_id").references(PreguntaTable.id).nullable()
    val orden = integer("orden")
    val textoRef = text("texto_ref").nullable()
    val recomendaciones = text("recomendaciones").nullable()
    val tiempoEntregaMs = integer("tiempo_entrega_ms").nullable()

    override val primaryKey = PrimaryKey(sesionPreguntaId)
}
