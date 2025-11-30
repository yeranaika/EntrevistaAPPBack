package data.models.cuestionario.prueba_practica

import kotlinx.serialization.Serializable

@Serializable
data class RespuestaPreguntaReq(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String>
)

@Serializable
data class EnviarRespuestasReq(
    val pruebaId: String,
    val respuestas: List<RespuestaPreguntaReq>
)

@Serializable
data class ResultadoPreguntaRes(
    val preguntaId: String,
    val correcta: Boolean,
    val claveCorrecta: String? = null,
    val seleccionadas: List<String> = emptyList()
)

@Serializable
data class EnviarRespuestasRes(
    val pruebaId: String,
    val totalPreguntas: Int,
    val respondidas: Int,
    val correctas: Int,
    val puntaje: Int,
    val detalle: List<ResultadoPreguntaRes>
)
