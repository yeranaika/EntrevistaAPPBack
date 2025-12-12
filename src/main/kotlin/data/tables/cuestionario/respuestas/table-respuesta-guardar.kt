package data.tables.cuestionario.respuestas

import data.tables.cuestionario.intentos_practica.IntentoPruebaTable
import data.tables.cuestionario.prueba.PruebaPreguntaTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import java.util.UUID

object RespuestaPruebaTable : Table("respuesta_prueba") {

    val respuestaPruebaId = uuid("respuesta_prueba_id")
        .clientDefault { UUID.randomUUID() }

    val intentoId = uuid("intento_id")
        .references(IntentoPruebaTable.intentoId, onDelete = ReferenceOption.CASCADE)

    // FK -> prueba_pregunta(prueba_pregunta_id)
    val pruebaPreguntaId = uuid("prueba_pregunta_id")
        .references(PruebaPreguntaTable.pruebaPreguntaId, onDelete = ReferenceOption.CASCADE)

    val respuestaUsuario = text("respuesta_usuario").nullable()
    val correcta = bool("correcta").nullable()
    val feedbackInspecl = text("feedback_inspecl").nullable()

    override val primaryKey =
        PrimaryKey(respuestaPruebaId, name = "PK_respuesta_prueba")
}
