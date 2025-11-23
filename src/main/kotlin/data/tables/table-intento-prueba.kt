package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object IntentoPruebaTable : Table("app.intento_prueba") {
    val id = uuid("intento_id")  // Agregado alias 'id' para compatibilidad
    val intentoId = uuid("intento_id")
    val usuarioId = uuid("usuario_id")
    val pruebaId = uuid("prueba_id")
    val fechaInicio = varchar("fecha_inicio", 50)
    val fechaFin = varchar("fecha_fin", 50).nullable()
    val puntaje = decimal("puntaje", 5, 2).nullable()  // Para puntaje decimal
    val puntajeTotal = integer("puntaje_total").default(0)
    val recomendaciones = text("recomendaciones").nullable()  // Para feedback/recomendaciones
    val estado = varchar("estado", 20).default("en_progreso")
    val tiempoTotalSegundos = integer("tiempo_total_segundos").nullable()
    val creadoEn = varchar("creado_en", 50)
    val actualizadoEn = varchar("actualizado_en", 50)

    override val primaryKey = PrimaryKey(intentoId)
}