package data.models

import kotlinx.serialization.Serializable

@Serializable
enum class TipoBanco { tec, soft, mix }

@Serializable
enum class Nivel { jr, mid, sr }

@Serializable
data class CreatePreguntaReq(
    val tipoBanco: TipoBanco,
    val nivel: Nivel,
    val sector: String,
    val metaCargo: String? = null,
    val texto: String,
    val pistas: Map<String, String>? = null,     // se guarda como JSON string
    val historica: Map<String, String>? = null,  // se guarda como JSON string
    val activa: Boolean = true
)

@Serializable
data class PreguntaRes(
    val id: String,
    val tipoBanco: TipoBanco,
    val nivel: Nivel,
    val sector: String? = null,
    val metaCargo: String? = null,
    val texto: String,
    val pistas: Map<String, String>?,     // ya parseado
    val historica: Map<String, String>?,  // ya parseado
    val activa: Boolean,
    val fechaCreacion: String             // ISO-8601
)

@Serializable
data class PagedPreguntasRes(
    val items: List<PreguntaRes>,
    val page: Int,
    val size: Int,
    val total: Long
)

@Serializable
data class ActualizarPreguntaReq(
    val texto: String? = null,
    val sector: String? = null,
    val activa: Boolean? = null,
    val pistas: Map<String, String>? = null,
    val historica: Map<String, String>? = null
)
