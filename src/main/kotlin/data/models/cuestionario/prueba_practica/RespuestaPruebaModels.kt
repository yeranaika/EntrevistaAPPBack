
/* src/main/kotlin/data/models/cuestionario/prueba_practica/RespuestaPruebaModels.kt */

package data.models.cuestionario.prueba_practica

import kotlinx.serialization.Serializable
import services.FeedbackGeneralV2  

@Serializable
data class RespuestaPreguntaReq(
    val preguntaId: String,
    val opcionesSeleccionadas: List<String> = emptyList(),
    val textoLibre: String? = null
)

@Serializable
data class EnviarRespuestasReq(
    val pruebaId: String,
    val respuestas: List<RespuestaPreguntaReq>,
    val usarIaFeedback: Boolean? = null
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
    val detalle: List<ResultadoPreguntaRes>,
    val feedbackGeneral: String,
    val feedbackGeneralV2: FeedbackGeneralV2? = null, // 👈 NUEVO
    val feedbackMode: String,
    val iaRevisionesRestantes: Int?
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
