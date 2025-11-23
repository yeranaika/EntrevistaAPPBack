package data.models.requisitos_cargo

import kotlinx.serialization.Serializable

/**
 * Representa una habilidad individual, opcional si la necesitas a nivel de dominio.
 */
@Serializable
data class SkillDto(
    val id: String,
    val cargo: String,
    val tipo: String,
    val descripcion: String
)

/**
 * Respuesta que agrupa habilidades técnicas y blandas.
 */
@Serializable
data class SkillsResponse(
    val tecnicas: List<String>,
    val blandas: List<String>
)
