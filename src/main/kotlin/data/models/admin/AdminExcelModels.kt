package data.models.admin

import java.time.LocalDateTime

data class UsuarioExcelRow(
    val nombre: String?,
    val correo: String,
    val rol: String,
    val estado: String,
    val idioma: String?,
    val telefono: String?,
    val origenRegistro: String,
    val fechaRegistro: LocalDateTime,
    val fechaUltimoLogin: LocalDateTime?,
    
    // Suscripcion
    val planSuscripcion: String?,     // free, premium
    val estadoSuscripcion: String?,   // activa, inactiva
    val fechaExpiracion: LocalDateTime?,

    // Objetivo Carrera
    val nombreCargo: String?,
    val sector: String?,
    val objetivoActivo: Boolean?,

    // Metricas Entrevistas
    val totalEntrevistas: Long,
    val fechaUltimaEntrevista: LocalDateTime?,
    val puntajePromedio: Double?
)

// DTO para objetivos de carrera en Excel
data class ObjetivoCarreraExcel(
    val correo: String,
    val nombre: String?,
    val nombreCargo: String,
    val sector: String?,
    val activo: Boolean
)
