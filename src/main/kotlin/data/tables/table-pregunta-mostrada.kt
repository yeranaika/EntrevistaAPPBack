package com.example.data.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object PreguntaMostradaTable : Table("pregunta_mostrada") {
    val id = uuid("id")
    val intentoId = uuid("intento_id").references(IntentoPruebaTable.intentoId)
    val preguntaId = uuid("pregunta_id")
    val orden = integer("orden")
    val mostradaEn = timestamp("mostrada_en")

    override val primaryKey = PrimaryKey(id)
}