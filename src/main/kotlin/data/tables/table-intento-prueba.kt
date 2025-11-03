package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object IntentoPruebaTable : Table("intento_prueba") {
    val intentoId = uuid("intento_id").autoGenerate()
    val usuarioId = uuid("usuario_id")
    val pruebaId = uuid("prueba_id")
    val fechaInicio = varchar("fecha_inicio", 50)
    val fechaFin = varchar("fecha_fin", 50).nullable()
    val puntajeTotal = integer("puntaje_total").default(0)
    val estado = varchar("estado", 20).default("en_progreso")
    val tiempoTotalSegundos = integer("tiempo_total_segundos").nullable()
    val creadoEn = varchar("creado_en", 50)
    val actualizadoEn = varchar("actualizado_en", 50)

    override val primaryKey = PrimaryKey(intentoId)
}