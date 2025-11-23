package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object PreguntaMostradaTable : Table("pregunta_mostrada") {
    val id = uuid("id").autoGenerate()
    val intentoId = uuid("intento_id").references(IntentoPruebaTable.id)
    val preguntaId = uuid("pregunta_id")
    val orden = integer("orden")
    val mostradaEn = varchar("mostrada_en", 50)

    override val primaryKey = PrimaryKey(id)
}