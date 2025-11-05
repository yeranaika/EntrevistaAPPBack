package com.example.data.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

// ============================================
// MODELOS DE DOMINIO (Base de datos)
// ============================================

@Serializable
data class IntentoPrueba(
    val intentoId: String,
    val usuarioId: String,
    val pruebaId: String,
    val fechaInicio: String, // ISO-8601
    val fechaFin: String? = null,
    val puntajeTotal: Int = 0,
    val estado: EstadoIntento = EstadoIntento.EN_PROGRESO,
    val tiempoTotalSegundos: Int? = null,
    val creadoEn: String,
    val actualizadoEn: String
)

@Serializable
enum class EstadoIntento {
    EN_PROGRESO,
    FINALIZADO,
    ABANDONADO
}

@Serializable
data class RespuestaPrueba(
    val respuestaId: String,
    val intentoId: String,
    val preguntaId: String,
    val respuestaUsuario: String,
    val esCorrecta: Boolean? = null,
    val puntajeObtenido: Int = 0,
    val tiempoRespuestaSegundos: Int? = null,
    val orden: Int,
    val creadoEn: String
)

@Serializable
data class PreguntaMostrada(
    val id: String,
    val intentoId: String,
    val preguntaId: String,
    val orden: Int,
    val mostradaEn: String
)

// ============================================
// DTOs PARA API (Request/Response)
// ============================================

@Serializable
data class CrearIntentoRequest(
    val pruebaId: String
)

@Serializable
data class CrearIntentoResponse(
    val intentoId: String,
    val pruebaId: String,
    val fechaInicio: String,
    val estado: EstadoIntento,
    val primeraPregunta: PreguntaConOrden? = null
)

@Serializable
data class PreguntaConOrden(
    val preguntaId: String,
    val orden: Int,
    val textoPregunta: String,
    val opciones: List<OpcionRespuesta>? = null,
    val tipoPregunta: String
)

@Serializable
data class OpcionRespuesta(
    val id: String,
    val texto: String
)

@Serializable
data class ResponderItemRequest(
    val preguntaId: String,
    val respuestaUsuario: String,
    val tiempoRespuestaSegundos: Int? = null
)

@Serializable
data class ResponderItemResponse(
    val respuestaId: String,
    val esCorrecta: Boolean? = null, // null si no se califica inmediatamente
    val puntajeObtenido: Int,
    val mensaje: String? = null,
    val siguientePregunta: PreguntaConOrden? = null
)

@Serializable
data class FinalizarIntentoRequest(
    val abandonado: Boolean = false
)

@Serializable
data class FinalizarIntentoResponse(
    val intentoId: String,
    val puntajeTotal: Int,
    val totalPreguntas: Int,
    val respuestasCorrectas: Int,
    val porcentajeAciertos: Double,
    val tiempoTotalSegundos: Int,
    val estado: EstadoIntento,
    val recomendaciones: List<String> = emptyList()
)

@Serializable
data class ObtenerSiguientePreguntaResponse(
    val pregunta: PreguntaConOrden?,
    val progreso: ProgresoIntento
)

@Serializable
data class ProgresoIntento(
    val preguntasRespondidas: Int,
    val totalPreguntas: Int,
    val porcentajeCompletado: Double
)

@Serializable
data class EstadisticasIntento(
    val intentoId: String,
    val usuarioNombre: String,
    val pruebaTitulo: String,
    val fechaInicio: String,
    val fechaFin: String?,
    val puntajeTotal: Int,
    val estado: EstadoIntento,
    val tiempoTotal: Int?,
    val totalRespuestas: Int,
    val respuestasCorrectas: Int,
    val porcentajeAciertos: Double
)

// ============================================
// DTO PARA LISTADO DE INTENTOS
// ============================================

@Serializable
data class IntentoResponse(
    val intentoId: String,
    val pruebaId: String,
    val pruebaTitulo: String? = null,
    val fechaInicio: String,
    val fechaFin: String? = null,
    val puntajeTotal: Int,
    val estado: EstadoIntento,
    val tiempoTotalSegundos: Int? = null
)

@Serializable
data class MisIntentosResponse(
    val intentos: List<IntentoResponse>,
    val total: Int
)

// ============================================
// MODELOS PARA LÓGICA INTERNA
// ============================================

data class PreguntaParaIntento(
    val preguntaId: UUID,
    val texto: String,
    val tipoPregunta: String,
    val respuestaCorrecta: String?,
    val opciones: List<OpcionDB>?
)

data class OpcionDB(
    val opcionId: UUID,
    val texto: String,
    val esCorrecta: Boolean
)