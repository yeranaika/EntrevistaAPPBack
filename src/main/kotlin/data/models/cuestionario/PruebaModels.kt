package data.models.cuestionario

import com.example.data.models.OpcionRespuesta
import kotlinx.serialization.Serializable

@Serializable
data class AsociarPreguntaRequest(
    val preguntaId: String,
    val orden: Int,
    val opciones: List<OpcionRespuesta>? = null,
    val claveCorrecta: String? = null
)

@Serializable
data class PreguntaAsignadaResponse(
    val pruebaPreguntaId: String,
    val pruebaId: String,
    val preguntaId: String,
    val orden: Int,
    val textoPregunta: String,
    val tipoPregunta: String,
    val opciones: List<OpcionRespuesta>? = null,
    val claveCorrecta: String? = null
)
