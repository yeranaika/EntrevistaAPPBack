package data.tables.nivelacion

import data.tables.usuarios.UsuarioTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Tabla para el historial de tests de nivelación completados por usuarios
 */
object TestNivelacionTable : UUIDTable("app.test_nivelacion", "test_id") {

    // Usuario que realizó el test
    val usuarioId = reference(
        name = "usuario_id",
        refColumn = UsuarioTable.usuarioId,
        onDelete = ReferenceOption.CASCADE
    )

    // Habilidad evaluada
    val habilidad = varchar("habilidad", 50)

    // Puntaje obtenido (0-100)
    val puntaje = integer("puntaje")

    // Total de preguntas respondidas
    val totalPreguntas = integer("total_preguntas")

    // Preguntas correctas
    val preguntasCorrectas = integer("preguntas_correctas")

    // Nivel sugerido basado en el puntaje (1=básico, 2=intermedio, 3=avanzado)
    val nivelSugerido = integer("nivel_sugerido")

    // Feedback generado para el usuario
    val feedback = text("feedback").nullable()

    // Fecha en que se completó el test
    val fechaCompletado = timestampWithTimeZone("fecha_completado")
}
