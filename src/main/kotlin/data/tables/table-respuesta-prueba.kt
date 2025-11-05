package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object RespuestaPruebaTable : Table("respuesta_prueba") {
    val respuestaId = uuid("respuesta_id").autoGenerate()
    val intentoId = uuid("intento_id").references(IntentoPruebaTable.intentoId)
    val preguntaId = uuid("pregunta_id")
    val respuestaUsuario = text("respuesta_usuario")
    val esCorrecta = bool("es_correcta").nullable()
    val puntajeObtenido = integer("puntaje_obtenido").default(0)
    val tiempoRespuestaSegundos = integer("tiempo_respuesta_segundos").nullable()
    val orden = integer("orden")
    val creadoEn = varchar("creado_en", 50)

    override val primaryKey = PrimaryKey(respuestaId)
}