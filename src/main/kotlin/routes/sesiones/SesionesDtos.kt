package routes.sesiones

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request para crear una nueva sesión de entrevista.
 */
@Serializable
data class CrearSesionReq(
    val modo: String,   // tec, soft, mix
    val nivel: String   // jr, mid, sr
)

/**
 * Response al crear una sesión exitosamente.
 */
@Serializable
data class SesionCreadaRes(
    val sessionId: String,
    val modo: String,
    val nivel: String,
    val fechaInicio: String
)

/**
 * Response con la siguiente pregunta de la sesión.
 */
@Serializable
data class PreguntaRes(
    val sessionPreguntaId: String,
    val preguntaId: String,
    val texto: String,
    val pistas: JsonObject?,
    val orden: Int
)

/**
 * Request para responder una pregunta de la sesión.
 */
@Serializable
data class ResponderReq(
    val sessionPreguntaId: String,
    val texto: String
)

/**
 * Response con el feedback generado.
 * Reutiliza el mismo formato que FeedbackDto del servicio.
 */
@Serializable
data class FeedbackRes(
    val nivelFeedback: String,
    val enunciado: String,
    val aciertos: List<String>,
    val faltantes: List<String>
)

/**
 * Response con el resumen final de la sesión.
 */
@Serializable
data class ResumenFinalRes(
    val sessionId: String,
    val puntajeGeneral: Int?,
    val totalPreguntas: Int,
    val observaciones: String
)
