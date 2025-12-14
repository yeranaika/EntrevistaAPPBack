package data.models.cuestionario.intentos_practica

import kotlinx.serialization.Serializable

@Serializable
data class HistorialPracticaItemRes(
    val intentoId: String,
    val pruebaId: String,
    val tipoPrueba: String,
    val fechaFin: String?,
    val puntaje: Int?,
    val puntajeTotal: Int,
    val estado: String
)
