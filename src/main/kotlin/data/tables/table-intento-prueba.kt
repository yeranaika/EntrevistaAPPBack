/* src/main/kotlin/data/tables/table-intento-prueba.kt */

package com.example.data.tables

import org.jetbrains.exposed.sql.Table

object IntentoPruebaTable : Table("app.intento_prueba") {
    val id = uuid("intento_id")
    val usuarioId = uuid("usuario_id")
    val pruebaId = uuid("prueba_id")
    val fechaInicio = varchar("fecha_inicio", 50)
    val fechaFin = varchar("fecha_fin", 50).nullable()
    val puntaje = decimal("puntaje", 5, 2).nullable()
    val puntajeTotal = integer("puntaje_total").default(0)
    val recomendaciones = text("recomendaciones").nullable()
    val estado = varchar("estado", 20).default("en_progreso")
    val tiempoTotalSegundos = integer("tiempo_total_segundos").nullable()
    val creadoEn = varchar("creado_en", 50)
    val actualizadoEn = varchar("actualizado_en", 50)

    // ✅ NUEVO: guardamos el JSON estructurado como string
    // (la columna real en BD es JSONB: feedback_general_v2)
    val feedbackGeneralV2 = text("feedback_general_v2").nullable()

    override val primaryKey = PrimaryKey(id)
}
