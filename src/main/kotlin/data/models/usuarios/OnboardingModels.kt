package data.models.usuarios

import kotlinx.serialization.Serializable

/**
 * Body que manda la app para guardar el objetivo del usuario
 * (área, meta/cargo y nivel).
 */
@Serializable
data class UpdateObjetivoReq(
    val area: String,
    val metaCargo: String,
    val nivel: String
)

/**
 * Resumen simple del objetivo (si quieres usarlo en algún endpoint).
 */
@Serializable
data class PlanResumenRes(
    val area: String,
    val metaCargo: String,
    val nivel: String
)

/**
 * Un “paso” o módulo del plan de práctica que devolvemos al cliente.
 *
 * OJO:
 * - descripcion puede ser null (en la BD es nullable)
 * - sesionesPorSemana puede ser null (en la BD es nullable)
 * - id es opcional, el servidor lo rellena al leer desde la BD
 */
@Serializable
data class PlanPracticaPasoDto(
    val id: String? = null,           
    val titulo: String,
    val descripcion: String? = null,
    val sesionesPorSemana: Int? = null
)

/**
 * Plan de práctica completo (lo usa:
 *  - la ruta admin para guardar el plan
 *  - la ruta /plan-practica para devolverlo al usuario
 */
@Serializable
data class PlanPracticaRes(
    val area: String,
    val metaCargo: String,
    val nivel: String,
    val pasos: List<PlanPracticaPasoDto>
)
