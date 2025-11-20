package data.models

import kotlinx.serialization.Serializable

// ========== DTOs para Preguntas de Nivelación ==========

@Serializable
data class PreguntaNivelacionRes(
    val id: String,
    val enunciado: String,
    val opciones: List<String>,
    val dificultad: Int
)

@Serializable
data class CreatePreguntaNivelacionReq(
    val habilidad: String,
    val dificultad: Int,
    val enunciado: String,
    val opciones: List<String>,
    val respuestaCorrecta: Int,
    val explicacion: String? = null,
    val activa: Boolean = true
)

// ========== DTOs para Tests de Nivelación ==========

@Serializable
data class TestNivelacionRes(
    val habilidad: String,
    val preguntas: List<PreguntaNivelacionRes>,
    val totalPreguntas: Int
)

@Serializable
data class RespuestaUsuario(
    val preguntaId: String,
    val respuestaSeleccionada: Int  // Índice de la opción seleccionada (0-based)
)

@Serializable
data class ResponderTestReq(
    val habilidad: String,
    val respuestas: List<RespuestaUsuario>
)

@Serializable
data class ResultadoTestRes(
    val testId: String,
    val habilidad: String,
    val puntaje: Int,
    val totalPreguntas: Int,
    val preguntasCorrectas: Int,
    val nivelSugerido: String,  // "básico", "intermedio", "avanzado"
    val feedback: String,
    val detalleRespuestas: List<DetalleRespuesta>
)

@Serializable
data class DetalleRespuesta(
    val preguntaId: String,
    val enunciado: String,
    val respuestaUsuario: Int,
    val respuestaCorrecta: Int,
    val esCorrecta: Boolean,
    val explicacion: String?
)

@Serializable
data class HistorialTestRes(
    val id: String,
    val habilidad: String,
    val puntaje: Int,
    val nivelSugerido: String,
    val fechaCompletado: String
)
