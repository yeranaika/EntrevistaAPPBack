package data.tables.cuestionario.intentos_practica

import data.tables.cuestionario.prueba.PruebaTable
import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.sql.Table
import java.util.UUID

object IntentoPruebaTable : Table("intento_prueba") {

    val id = uuid("intento_id").clientDefault { UUID.randomUUID() }
    // aliases para que el resto del código funcione
    val intentoId = id

    val pruebaId = uuid("prueba_id").references(PruebaTable.pruebaId)
    val usuarioId = uuid("usuario_id").references(UsuarioTable.usuarioId)
    val usuarioIdCol = usuarioId

    val fechaInicio = varchar("fecha_inicio", 50)
    val fechaFin = varchar("fecha_fin", 50).nullable()

    val puntaje = decimal("puntaje", 5, 2).nullable()
    val recomendaciones = text("recomendaciones").nullable()
    val puntajeTotal = integer("puntaje_total").default(0)

    val estado = varchar("estado", 20).default("en_progreso")
    val tiempoTotalSegundos = integer("tiempo_total_segundos").nullable()

    val creadoEn = varchar("creado_en", 50)
    val actualizadoEn = varchar("actualizado_en", 50)

    override val primaryKey = PrimaryKey(id)
}
