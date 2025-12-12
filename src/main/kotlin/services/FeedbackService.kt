/* src/main/kotlin/services/FeedbackService.kt */

package services

import kotlinx.serialization.Serializable

/**
 * Interfaz para generación de feedback automático en sesiones de entrevista.
 * Permite implementaciones mock (MVP) y futuras con IA.
 */
interface FeedbackService {
    /**
     * Genera retroalimentación basada en la pregunta, respuesta del usuario y nivel.
     *
     * @param preguntaTexto Texto de la pregunta formulada
     * @param respuestaTexto Texto de la respuesta del usuario
     * @param nivel Nivel de dificultad (jr, mid, sr)
     * @return DTO con feedback generado
     */
    suspend fun generarFeedback(
        preguntaTexto: String,
        respuestaTexto: String,
        nivel: String
    ): FeedbackDto
}

/**
 * Implementación mock del servicio de feedback para MVP.
 * Genera retroalimentación genérica sin procesamiento de IA.
 *
 * En el futuro, esta implementación será reemplazada por una que use
 * modelos de IA (OpenAI, Anthropic, etc.) para análisis más profundo.
 */
class MockFeedbackService : FeedbackService {
    override suspend fun generarFeedback(
        preguntaTexto: String,
        respuestaTexto: String,
        nivel: String
    ): FeedbackDto {
        // Validación básica de longitud de respuesta
        val palabras = respuestaTexto.trim().split(Regex("\\s+"))
        val longitudAdecuada = palabras.size >= 20

        return when (nivel) {
            "jr" -> FeedbackDto(
                nivelFeedback = "free",
                enunciado = if (longitudAdecuada) {
                    "Tu respuesta cubre los aspectos básicos del tema. " +
                    "Para nivel junior, demuestras comprensión de los conceptos fundamentales."
                } else {
                    "Tu respuesta es muy breve. Intenta profundizar más en los conceptos " +
                    "y proporcionar ejemplos concretos."
                },
                aciertos = if (longitudAdecuada) {
                    listOf(
                        "Explicación clara de conceptos principales",
                        "Estructura ordenada de respuesta",
                        "Uso apropiado de terminología técnica"
                    )
                } else {
                    listOf("Mención de conceptos clave")
                },
                faltantes = if (longitudAdecuada) {
                    listOf(
                        "Agregar ejemplos concretos de implementación",
                        "Mencionar posibles casos de uso comunes",
                        "Detallar ventajas y desventajas del enfoque"
                    )
                } else {
                    listOf(
                        "Explicación más detallada de los conceptos",
                        "Ejemplos de uso práctico",
                        "Mayor desarrollo de las ideas presentadas"
                    )
                }
            )

            "mid" -> FeedbackDto(
                nivelFeedback = "free",
                enunciado = if (longitudAdecuada) {
                    "Tu respuesta muestra conocimiento intermedio. " +
                    "Consideras aspectos técnicos importantes, aunque hay espacio para profundizar."
                } else {
                    "Para nivel mid, se esperan respuestas más elaboradas que incluyan " +
                    "consideraciones de arquitectura y mejores prácticas."
                },
                aciertos = if (longitudAdecuada) {
                    listOf(
                        "Comprensión sólida de conceptos técnicos",
                        "Mención de consideraciones prácticas",
                        "Estructura lógica en la explicación"
                    )
                } else {
                    listOf("Conocimiento básico del tema")
                },
                faltantes = if (longitudAdecuada) {
                    listOf(
                        "Profundizar en trade-offs y decisiones de diseño",
                        "Mencionar herramientas o frameworks específicos",
                        "Incluir consideraciones de escalabilidad o rendimiento",
                        "Discutir alternativas y cuándo usarlas"
                    )
                } else {
                    listOf(
                        "Análisis más profundo del problema",
                        "Ejemplos de implementación real",
                        "Consideraciones de arquitectura",
                        "Discusión de alternativas"
                    )
                }
            )

            "sr" -> FeedbackDto(
                nivelFeedback = "free",
                enunciado = if (longitudAdecuada) {
                    "Tu respuesta demuestra conocimiento del tema. " +
                    "Para nivel senior, se valora el análisis de trade-offs, experiencia práctica " +
                    "y consideraciones de arquitectura a gran escala."
                } else {
                    "Para nivel senior, se esperan respuestas exhaustivas que cubran " +
                    "arquitectura, escalabilidad, trade-offs y lecciones de experiencia real."
                },
                aciertos = if (longitudAdecuada) {
                    listOf(
                        "Conocimiento técnico avanzado",
                        "Presentación estructurada de ideas",
                        "Consciencia de la complejidad del problema"
                    )
                } else {
                    listOf("Identificación del tema principal")
                },
                faltantes = if (longitudAdecuada) {
                    listOf(
                        "Análisis profundo de trade-offs entre diferentes enfoques",
                        "Consideraciones de escalabilidad y rendimiento a nivel de sistema",
                        "Experiencias reales y lecciones aprendidas",
                        "Impacto en la organización y procesos de desarrollo",
                        "Estrategias de migración o implementación gradual"
                    )
                } else {
                    listOf(
                        "Desarrollo comprehensivo del tema",
                        "Análisis de trade-offs arquitectónicos",
                        "Experiencia práctica y casos reales",
                        "Consideraciones de escalabilidad",
                        "Impacto organizacional y técnico"
                    )
                }
            )

            else -> FeedbackDto(
                nivelFeedback = "free",
                enunciado = "Tu respuesta ha sido registrada. Continúa practicando para mejorar tus habilidades.",
                aciertos = listOf("Respuesta proporcionada"),
                faltantes = listOf("Profundizar en conceptos técnicos", "Agregar ejemplos concretos")
            )
        }
    }
}

/**
 * DTO para transferencia de feedback generado.
 * Se serializa en JSON para las respuestas de la API.
 */
@Serializable
data class FeedbackDto(
    val nivelFeedback: String,   // "free", "premium", etc.
    val enunciado: String,        // Mensaje general del feedback
    val aciertos: List<String>,   // Puntos positivos de la respuesta
    val faltantes: List<String>   // Áreas de mejora
)
