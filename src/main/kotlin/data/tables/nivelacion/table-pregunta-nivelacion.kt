package data.tables.nivelacion

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

/**
 * Tabla para preguntas de opción múltiple usadas en tests de nivelación
 */
object PreguntaNivelacionTable : UUIDTable("app.pregunta_nivelacion", "pregunta_id") {

    // Habilidad que evalúa esta pregunta (ej: "logica", "algoritmos", "estructuras_datos", "sql", etc.)
    val habilidad = varchar("habilidad", 50)

    // Nivel de dificultad (1=básico, 2=intermedio, 3=avanzado)
    val dificultad = integer("dificultad").default(1)

    // Enunciado de la pregunta
    val enunciado = text("enunciado")

    // Opciones de respuesta (JSON array: ["opcion1", "opcion2", "opcion3", "opcion4"])
    val opciones = text("opciones")

    // Índice de la respuesta correcta (0-based, ej: 0, 1, 2, 3)
    val respuestaCorrecta = integer("respuesta_correcta")

    // Explicación de por qué es la respuesta correcta (opcional)
    val explicacion = text("explicacion").nullable()

    // Si la pregunta está activa o no
    val activa = bool("activa").default(true)

    // Fecha de creación
    val fechaCreacion = timestampWithTimeZone("fecha_creacion")
}
