package com.example.data.repository

import com.example.data.models.*
import com.example.data.tables.IntentoPruebaTable
import com.example.data.tables.RespuestaPruebaTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

object IntentoPruebaRepository {

    // ============================================
    // CREAR INTENTO
    // ============================================
    suspend fun crearIntento(usuarioId: UUID, pruebaId: UUID): IntentoPrueba = transaction {
        val now = Instant.now()
        val intentoId = UUID.randomUUID()

        IntentoPruebaTable.insert {
            it[this.intentoId] = intentoId
            it[this.usuarioId] = usuarioId
            it[this.pruebaId] = pruebaId
            it[fechaInicio] = now
        }

        IntentoPrueba(
            intentoId = intentoId.toString(),
            usuarioId = usuarioId.toString(),
            pruebaId = pruebaId.toString(),
            fechaInicio = now.toString(),
            fechaFin = null,
            puntajeTotal = 0,
            estado = EstadoIntento.EN_PROGRESO,
            tiempoTotalSegundos = null,
            creadoEn = now.toString(),
            actualizadoEn = now.toString()
        )
    }

    // ============================================
    // OBTENER INTENTO
    // ============================================
    suspend fun obtenerIntento(intentoId: UUID, usuarioIdFromJWT: String? = null): IntentoPrueba? = transaction {
        IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .map { rowToIntento(it) }
            .singleOrNull()
    }

    // ============================================
    // OBTENER SIGUIENTE PREGUNTA
    // ============================================
    suspend fun obtenerSiguientePregunta(intentoId: UUID): PreguntaConOrden? = transaction {
        // 1. Obtener el intento para saber qué prueba es
        val intento = IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .singleOrNull() ?: return@transaction null

        val pruebaId = intento[IntentoPruebaTable.pruebaId]

        // 2. Obtener IDs de prueba_pregunta ya respondidas
        val preguntasRespondidasIds = RespuestaPruebaTable
            .select(RespuestaPruebaTable.pruebaPreguntaId)
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .map { it[RespuestaPruebaTable.pruebaPreguntaId] }

        // 3. Buscar la siguiente pregunta no respondida
        val siguientePregunta = exec("""
            SELECT 
                pp.prueba_pregunta_id,
                pp.orden,
                p.texto,
                p.tipo_banco,
                pp.opciones,
                pp.clave_correcta
            FROM app.prueba_pregunta pp
            JOIN app.pregunta p ON pp.pregunta_id = p.pregunta_id
            WHERE pp.prueba_id = '$pruebaId'
            ${if (preguntasRespondidasIds.isNotEmpty()) 
                "AND pp.prueba_pregunta_id NOT IN (${preguntasRespondidasIds.joinToString(",") { "'$it'" }})" 
                else ""}
            ORDER BY pp.orden
            LIMIT 1
        """.trimIndent()) { rs ->
            if (rs.next()) {
                PreguntaConOrden(
                    preguntaId = rs.getString("prueba_pregunta_id"),
                    orden = rs.getInt("orden"),
                    textoPregunta = rs.getString("texto"),
                    opciones = null, // Las opciones están en JSON, parsear si es necesario
                    tipoPregunta = rs.getString("tipo_banco")
                )
            } else {
                null
            }
        }

        siguientePregunta
    }

    // ============================================
    // GUARDAR RESPUESTA
    // ============================================
    suspend fun guardarRespuesta(
        intentoId: UUID,
        preguntaId: UUID, // Este es prueba_pregunta_id
        respuestaUsuario: String,
        esCorrecta: Boolean? = null,
        puntajeObtenido: Int = 0,
        tiempoRespuestaSegundos: Int? = null,
        orden: Int
    ): RespuestaPrueba = transaction {
        val now = Instant.now()
        val respuestaId = UUID.randomUUID()

        RespuestaPruebaTable.insert {
            it[this.respuestaId] = respuestaId
            it[this.intentoId] = intentoId
            it[pruebaPreguntaId] = preguntaId
            it[this.respuestaUsuario] = respuestaUsuario
            it[correcta] = esCorrecta
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
            creadoEn = now.toString()
        )
    }

    // ============================================
    // VERIFICAR SI RESPUESTA ES CORRECTA
    // ============================================
    suspend fun verificarRespuesta(
        preguntaId: UUID, // Este es prueba_pregunta_id
        respuestaUsuario: String
    ): Boolean = transaction {
        // Buscar la clave correcta en prueba_pregunta
        val claveCorrecta = exec("""
            SELECT clave_correcta
            FROM app.prueba_pregunta
            WHERE prueba_pregunta_id = '$preguntaId'
            LIMIT 1
        """.trimIndent()) { rs ->
            if (rs.next()) rs.getString("clave_correcta") else null
        }

        // Comparar (case-insensitive)
        claveCorrecta?.equals(respuestaUsuario, ignoreCase = true) ?: false
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
        val now = Instant.now()
        
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
        val respuestasCorrectas = respuestas.count { it[RespuestaPruebaTable.correcta] == true }
        
        // Calcular puntaje (0-100)
        val puntaje = if (totalPreguntas > 0) {
            BigDecimal((respuestasCorrectas.toDouble() / totalPreguntas) * 100)
                .setScale(2, java.math.RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }
        
        // Calcular tiempo total
        val fechaInicio = intento[IntentoPruebaTable.fechaInicio]
        val tiempoTotal = (now.epochSecond - fechaInicio.epochSecond).toInt()

        // Generar recomendaciones
        val porcentajeAciertos = puntaje.toDouble()
        val recomendacionesTexto = generarRecomendaciones(porcentajeAciertos, totalPreguntas)
            .joinToString("\n")

        // Actualizar intento
        IntentoPruebaTable.update({ IntentoPruebaTable.intentoId eq intentoId }) {
            it[fechaFin] = now
            it[this.puntaje] = puntaje
            it[recomendaciones] = recomendacionesTexto
        }

        FinalizarIntentoResponse(
            intentoId = intentoId.toString(),
            puntajeTotal = puntaje.toInt(),
            totalPreguntas = totalPreguntas,
            respuestasCorrectas = respuestasCorrectas,
            porcentajeAciertos = porcentajeAciertos,
            tiempoTotalSegundos = tiempoTotal,
            estado = if (abandonado) EstadoIntento.ABANDONADO else EstadoIntento.FINALIZADO,
            recomendaciones = generarRecomendaciones(porcentajeAciertos, totalPreguntas)
        )
    }

    // ============================================
    // OBTENER PROGRESO
    // ============================================
    suspend fun obtenerProgreso(intentoId: UUID): ProgresoIntento = transaction {
        val respondidas = RespuestaPruebaTable
            .selectAll()
            .where { RespuestaPruebaTable.intentoId eq intentoId }
            .count()
            .toInt()

        // Obtener total de preguntas de la prueba
        val intento = IntentoPruebaTable
            .selectAll()
            .where { IntentoPruebaTable.intentoId eq intentoId }
            .singleOrNull() ?: return@transaction ProgresoIntento(0, 0, 0.0)
        
        val pruebaId = intento[IntentoPruebaTable.pruebaId]
        
        val totalPreguntas = exec("""
            SELECT COUNT(*) as total
            FROM app.prueba_pregunta
            WHERE prueba_id = '$pruebaId'
        """.trimIndent()) { rs ->
            if (rs.next()) rs.getInt("total") else 0
        } ?: 0

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
    // OBTENER ESTADISTICAS
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
        val respuestasCorrectas = respuestas.count { it[RespuestaPruebaTable.correcta] == true }
        val porcentaje = if (totalRespuestas > 0) {
            (respuestasCorrectas.toDouble() / totalRespuestas) * 100
        } else 0.0

        // Calcular tiempo total si está finalizado
        val tiempoTotal = intento.fechaFin?.let { fechaFin ->
            val inicio = Instant.parse(intento.fechaInicio)
            val fin = Instant.parse(fechaFin)
            (fin.epochSecond - inicio.epochSecond).toInt()
        }

        EstadisticasIntento(
            intentoId = intento.intentoId,
            usuarioNombre = "Usuario", // TODO: Join con usuario
            pruebaTitulo = "Prueba", // TODO: Join con prueba
            fechaInicio = intento.fechaInicio,
            fechaFin = intento.fechaFin,
            puntajeTotal = intento.puntajeTotal,
            estado = intento.estado,
            tiempoTotal = tiempoTotal,
            totalRespuestas = totalRespuestas,
            respuestasCorrectas = respuestasCorrectas,
            porcentajeAciertos = porcentaje
        )
    }

    // ============================================
    // FUNCIONES AUXILIARES
    // ============================================

    private fun rowToIntento(row: ResultRow): IntentoPrueba {
        val fechaInicio = row[IntentoPruebaTable.fechaInicio]
        val fechaFin = row[IntentoPruebaTable.fechaFin]
        
        // Inferir estado basado en fecha_fin
        val estado = when {
            fechaFin == null -> EstadoIntento.EN_PROGRESO
            else -> EstadoIntento.FINALIZADO
        }

        // Calcular tiempo total si está finalizado
        val tiempoTotal = fechaFin?.let {
            (it.epochSecond - fechaInicio.epochSecond).toInt()
        }

        return IntentoPrueba(
            intentoId = row[IntentoPruebaTable.intentoId].toString(),
            usuarioId = row[IntentoPruebaTable.usuarioId].toString(),
            pruebaId = row[IntentoPruebaTable.pruebaId].toString(),
            fechaInicio = fechaInicio.toString(),
            fechaFin = fechaFin?.toString(),
            puntajeTotal = row[IntentoPruebaTable.puntaje]?.toInt() ?: 0,
            estado = estado,
            tiempoTotalSegundos = tiempoTotal,
            creadoEn = fechaInicio.toString(),
            actualizadoEn = (fechaFin ?: fechaInicio).toString()
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