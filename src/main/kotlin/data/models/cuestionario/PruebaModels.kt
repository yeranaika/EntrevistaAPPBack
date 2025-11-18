package data.models.cuestionario

import com.example.data.models.OpcionRespuesta
import data.models.Nivel
import kotlinx.serialization.Serializable

// ============================================
// ENUMS PARA PRUEBAS
// ============================================

@Serializable
enum class AreaPrueba { tec, soft, mix }

// ============================================
// MODELOS PARA ASOCIAR PREGUNTAS A PRUEBAS
// ============================================

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

// ============================================
// MODELOS PARA CREAR Y GESTIONAR PRUEBAS
// ============================================

@Serializable
data class CrearPruebaReq(
    val tipoPrueba: String = "aprendiz",  // "aprendiz", etc.
    val area: AreaPrueba? = null,
    val nivel: Nivel? = null,
    val metadata: Map<String, String>? = null  // Cambiado a objeto
)

@Serializable
data class PruebaRes(
    val pruebaId: String,
    val tipoPrueba: String,
    val area: AreaPrueba? = null,
    val nivel: Nivel? = null,
    val metadata: Map<String, String>? = null,
    val historica: Map<String, String>? = null,
    val activo: Boolean
)

@Serializable
data class PruebaCompletaRes(
    val pruebaId: String,
    val tipoPrueba: String,
    val area: AreaPrueba? = null,
    val nivel: Nivel? = null,
    val metadata: Map<String, String>? = null,
    val historica: Map<String, String>? = null,
    val activo: Boolean,
    val preguntas: List<PreguntaAsignadaResponse>
)

@Serializable
data class ActualizarPruebaReq(
    val tipoPrueba: String? = null,
    val area: AreaPrueba? = null,
    val nivel: Nivel? = null,
    val metadata: Map<String, String>? = null,
    val activo: Boolean? = null
)

@Serializable
data class PagedPruebasRes(
    val items: List<PruebaRes>,
    val page: Int,
    val size: Int,
    val total: Long
)
