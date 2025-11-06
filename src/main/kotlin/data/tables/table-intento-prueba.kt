package com.example.data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object IntentoPruebaTable : Table("intento_prueba") {
    val intentoId = uuid("intento_id")
    val pruebaId = uuid("prueba_id")
    val usuarioId = uuid("usuario_id")
    val fechaInicio = timestamp("fecha_inicio")
    val fechaFin = timestamp("fecha_fin").nullable()
    val puntaje = decimal("puntaje", 5, 2).nullable()
    val recomendaciones = text("recomendaciones").nullable()

    override val primaryKey = PrimaryKey(intentoId)
}