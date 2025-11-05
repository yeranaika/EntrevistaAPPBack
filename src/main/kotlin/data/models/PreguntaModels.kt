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
    val sector: String? = null,
    val texto: String,
    val pistas: Map<String, String>? = null,
    val historica: Map<String, String>? = null,
    val activa: Boolean = true
)

@Serializable
data class PreguntaRes(
    val id: String,
    val tipoBanco: TipoBanco,
    val nivel: Nivel,
    val sector: String? = null,
    val texto: String,
    val pistas: Map<String, String>? = null,
    val historica: Map<String, String>? = null,
    val activa: Boolean,
    val fechaCreacion: String
)

@Serializable
data class PagedPreguntasRes(
    val items: List<PreguntaRes>,
    val page: Int,
    val size: Int,
    val total: Long
)
