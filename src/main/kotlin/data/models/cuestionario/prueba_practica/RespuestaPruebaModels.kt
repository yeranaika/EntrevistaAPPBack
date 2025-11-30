package data.models.cuestionario.prueba_practica

import kotlinx.serialization.Serializable

@Serializable
data class RespuestaPreguntaReq(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String> = emptyList(),
    val textoLibre: String? = null
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

@Serializable
data class IntentoPracticaRes(
    val intentoId: String,
    val pruebaId: String,
    val fecha: String,
    val puntaje: Int,
    val totalPreguntas: Int,
    val correctas: Int
)

@Serializable
data class HistorialPracticaRes(
    val items: List<IntentoPracticaRes>
)
