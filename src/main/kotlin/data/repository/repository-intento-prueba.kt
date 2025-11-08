package com.example.data.repository

import com.example.data.models.*
import com.example.data.tables.IntentoPruebaTable
import com.example.data.tables.RespuestaPruebaTable
import com.example.data.tables.PreguntaMostradaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

object IntentoPruebaRepository {

    // ============================================
    // CREAR INTENTO
    // ============================================
    suspend fun crearIntento(usuarioId: UUID, pruebaId: UUID): IntentoPrueba = transaction {
        val now = Instant.now().toString()
        val intentoId = UUID.randomUUID()

        IntentoPruebaTable.insert {
            it[this.intentoId] = intentoId
            it[this.usuarioId] = usuarioId
            it[this.pruebaId] = pruebaId
            it[fechaInicio] = now
            it[estado] = "en_progreso"
            it[creadoEn] = now
            it[actualizadoEn] = now
        }

        IntentoPrueba(
            intentoId = intentoId.toString(),
            usuarioId = usuarioId.toString(),
            pruebaId = pruebaId.toString(),
            fechaInicio = now,
            fechaFin = null,
            puntajeTotal = 0,
            estado = EstadoIntento.EN_PROGRESO,
            tiempoTotalSegundos = null,
            creadoEn = now,
            actualizadoEn = now
        )
    }

    // ============================================
    // OBTENER INTENTO
    // ============================================
    suspend fun obtenerIntento(intentoId: UUID): IntentoPrueba? = transaction {
        IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .map { rowToIntento(it) }
            .singleOrNull()
    }

    // ============================================
    // OBTENER SIGUIENTE PREGUNTA (placeholder)
    // ============================================
    suspend fun obtenerSiguientePregunta(intentoId: UUID): PreguntaConOrden? = transaction {
        // TODO: Implementar segun tu modelo de preguntas
        // Por ahora retorna null
        null
    }

    // ============================================
    // GUARDAR RESPUESTA
    // ============================================
    suspend fun guardarRespuesta(
        intentoId: UUID,
        preguntaId: UUID,
        respuestaUsuario: String,
        esCorrecta: Boolean? = null,
        puntajeObtenido: Int = 0,
        tiempoRespuestaSegundos: Int? = null,
        orden: Int
    ): RespuestaPrueba = transaction {
        val now = Instant.now().toString()
        val respuestaId = UUID.randomUUID()

        RespuestaPruebaTable.insert {
            it[this.respuestaId] = respuestaId
            it[this.intentoId] = intentoId
            it[this.preguntaId] = preguntaId
            it[this.respuestaUsuario] = respuestaUsuario
            it[this.esCorrecta] = esCorrecta
            it[this.puntajeObtenido] = puntajeObtenido
            it[this.tiempoRespuestaSegundos] = tiempoRespuestaSegundos
            it[this.orden] = orden
            it[creadoEn] = now
        }

        RespuestaPrueba(
            respuestaId = respuestaId.toString(),
            intentoId = intentoId.toString(),
            preguntaId = preguntaId.toString(),
            respuestaUsuario = respuestaUsuario,
            esCorrecta = esCorrecta,
            puntajeObtenido = puntajeObtenido,
            tiempoRespuestaSegundos = tiempoRespuestaSegundos,
            orden = orden,
            creadoEn = now
        )
    }

    // ============================================
    // VERIFICAR SI RESPUESTA ES CORRECTA (placeholder)
    // ============================================
    suspend fun verificarRespuesta(
        preguntaId: UUID,
        respuestaUsuario: String
    ): Boolean = transaction {
        // TODO: Implementar verificacion segun tu modelo de preguntas
        // Por ahora retorna false
        false
    }

    // ============================================
    // CONTAR RESPUESTAS DEL INTENTO
    // ============================================
    suspend fun contarRespuestas(intentoId: UUID): Int = transaction {
        RespuestaPruebaTable
            .selectAll()
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .count()
            .toInt()
    }

    // ============================================
    // FINALIZAR INTENTO
    // ============================================
    suspend fun finalizarIntento(
        intentoId: UUID,
        abandonado: Boolean = false
    ): FinalizarIntentoResponse = transaction {
        val now = Instant.now().toString()
        
        // Obtener intento
        val intento = IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .singleOrNull() ?: throw IllegalArgumentException("Intento no encontrado")

        // Obtener todas las respuestas
        val respuestas = RespuestaPruebaTable
            .selectAll()
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .toList()

        val totalPreguntas = respuestas.size
        val respuestasCorrectas = respuestas.count { it[RespuestaPruebaTable.esCorrecta] == true }
        val puntajeTotal = respuestas.sumOf { it[RespuestaPruebaTable.puntajeObtenido] }
        
        // Calcular tiempo total
        val fechaInicio = Instant.parse(intento[IntentoPruebaTable.fechaInicio])
        val tiempoTotal = (Instant.now().epochSecond - fechaInicio.epochSecond).toInt()

        // Actualizar intento
        IntentoPruebaTable.update({ IntentoPruebaTable.intentoId eq intentoId }) {
            it[fechaFin] = now
            it[this.puntajeTotal] = puntajeTotal
            it[estado] = if (abandonado) "abandonado" else "finalizado"
            it[tiempoTotalSegundos] = tiempoTotal
            it[actualizadoEn] = now
        }

        val porcentajeAciertos = if (totalPreguntas > 0) {
            (respuestasCorrectas.toDouble() / totalPreguntas) * 100
        } else 0.0

        // Generar recomendaciones
        val recomendaciones = generarRecomendaciones(porcentajeAciertos, totalPreguntas)

        FinalizarIntentoResponse(
            intentoId = intentoId.toString(),
            puntajeTotal = puntajeTotal,
            totalPreguntas = totalPreguntas,
            respuestasCorrectas = respuestasCorrectas,
            porcentajeAciertos = porcentajeAciertos,
            tiempoTotalSegundos = tiempoTotal,
            estado = if (abandonado) EstadoIntento.ABANDONADO else EstadoIntento.FINALIZADO,
            recomendaciones = recomendaciones
        )
    }

    // ============================================
    // OBTENER PROGRESO
    // ============================================
    suspend fun obtenerProgreso(intentoId: UUID, totalPreguntas: Int): ProgresoIntento = transaction {
        val respondidas = RespuestaPruebaTable
            .selectAll()
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .count()
            .toInt()

        val porcentaje = if (totalPreguntas > 0) {
            (respondidas.toDouble() / totalPreguntas) * 100
        } else 0.0

        ProgresoIntento(
            preguntasRespondidas = respondidas,
            totalPreguntas = totalPreguntas,
            porcentajeCompletado = porcentaje
        )
    }

    // ============================================
    // OBTENER INTENTOS POR USUARIO
    // ============================================
    suspend fun obtenerIntentosPorUsuario(usuarioId: UUID): List<IntentoPrueba> = transaction {
        IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.usuarioId eq usuarioId }
            .orderBy(IntentoPruebaTable.fechaInicio to SortOrder.DESC)
            .map { rowToIntento(it) }
    }

    // ============================================
    // OBTENER ESTADISTICAS (simplificado)
    // ============================================
    suspend fun obtenerEstadisticas(intentoId: UUID): EstadisticasIntento? = transaction {
        val intento = IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .map { rowToIntento(it) }
            .singleOrNull() ?: return@transaction null
        
        val respuestas = RespuestaPruebaTable
            .selectAll()
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .toList()

        val totalRespuestas = respuestas.size
        val respuestasCorrectas = respuestas.count { it[RespuestaPruebaTable.esCorrecta] == true }
        val porcentaje = if (totalRespuestas > 0) {
            (respuestasCorrectas.toDouble() / totalRespuestas) * 100
        } else 0.0

        EstadisticasIntento(
            intentoId = intento.intentoId,
            usuarioNombre = "Usuario", // TODO: Obtener del join con usuario
            pruebaTitulo = "Prueba", // TODO: Obtener del join con prueba
            fechaInicio = intento.fechaInicio,
            fechaFin = intento.fechaFin,
            puntajeTotal = intento.puntajeTotal,
            estado = intento.estado,
            tiempoTotal = intento.tiempoTotalSegundos,
            totalRespuestas = totalRespuestas,
            respuestasCorrectas = respuestasCorrectas,
            porcentajeAciertos = porcentaje
        )
    }

    // ============================================
    // FUNCIONES AUXILIARES
    // ============================================

    private fun rowToIntento(row: ResultRow): IntentoPrueba {
        return IntentoPrueba(
            intentoId = row[IntentoPruebaTable.intentoId].toString(),
            usuarioId = row[IntentoPruebaTable.usuarioId].toString(),
            pruebaId = row[IntentoPruebaTable.pruebaId].toString(),
            fechaInicio = row[IntentoPruebaTable.fechaInicio],
            fechaFin = row[IntentoPruebaTable.fechaFin],
            puntajeTotal = row[IntentoPruebaTable.puntajeTotal],
            estado = when(row[IntentoPruebaTable.estado].lowercase()) {
                "en_progreso" -> EstadoIntento.EN_PROGRESO
                "finalizado" -> EstadoIntento.FINALIZADO
                "abandonado" -> EstadoIntento.ABANDONADO
                else -> EstadoIntento.EN_PROGRESO
            },
            tiempoTotalSegundos = row[IntentoPruebaTable.tiempoTotalSegundos],
            creadoEn = row[IntentoPruebaTable.creadoEn],
            actualizadoEn = row[IntentoPruebaTable.actualizadoEn]
        )
    }

    private fun generarRecomendaciones(porcentaje: Double, totalPreguntas: Int): List<String> {
        return when {
            porcentaje >= 90 -> listOf(
                "Excelente desempeño! Estas muy bien preparado.",
                "Considera practicar casos mas avanzados."
            )
            porcentaje >= 70 -> listOf(
                "Buen trabajo, pero hay areas de mejora.",
                "Revisa los temas donde fallaste.",
                "Practica mas preguntas similares."
            )
            porcentaje >= 50 -> listOf(
                "Necesitas mas practica en varios temas.",
                "Repasa los conceptos fundamentales.",
                "Considera estudiar mas antes de la entrevista real."
            )
            else -> listOf(
                "Te recomendamos estudiar mas antes de continuar.",
                "Enfocate en entender los conceptos basicos.",
                "Practica con ejemplos mas sencillos primero."
            )
        }
    }
}