package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object RespuestaPruebaTable : Table("respuesta_prueba") {
    val respuestaId = uuid("respuesta_prueba_id")
    val intentoId = uuid("intento_id").references(IntentoPruebaTable.intentoId)
    val pruebaPreguntaId = uuid("prueba_pregunta_id")
    val respuestaUsuario = text("respuesta_usuario").nullable()
    val correcta = bool("correcta").nullable()
    val feedbackInspecl = text("feedback_inspecl").nullable()

    override val primaryKey = PrimaryKey(respuestaId)
}