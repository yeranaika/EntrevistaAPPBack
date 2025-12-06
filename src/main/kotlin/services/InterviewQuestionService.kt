package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class OpenAIChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIChatRequest(
    val model: String,
    val messages: List<OpenAIChatMessage>,
    val temperature: Double = 0.4
)

@Serializable
data class OpenAIChoiceMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIChoice(
    val index: Int,
    val message: OpenAIChoiceMessage
)

@Serializable
data class OpenAIChatResponse(
    val choices: List<OpenAIChoice>
)

/**
 * DTO para preguntas de selección múltiple (versión antigua usada por TestNivelacionRoutes).
 */
@Serializable
data class GeneratedQuestionDto(
    val enunciado: String,
    val opciones: List<String>,
    val respuestaCorrecta: Int, // índice 0-based
    val explicacion: String,
    val dificultad: Int         // 1=básico, 2=intermedio, 3=avanzado
)

/**
 * Opción de una pregunta de selección única (para las preguntas mixtas tipo IAJOB).
 */
@Serializable
data class OpcionDto(
    val id: String,     // "A", "B", "C", etc.
    val texto: String
)

/**
 * Pregunta mixta generada por la IA:
 * puede ser "seleccion_unica" o "abierta_texto".
 */
@Serializable
data class MixedGeneratedQuestionDto(
    val tipo: String,                       // "seleccion_unica" | "abierta_texto"
    val nivel: String? = null,             // opcional: "jr" | "medio" | "sr"
    val enunciado: String,
    val pistas: List<String> = emptyList(),
    val opciones: List<OpcionDto> = emptyList(),
    val respuesta_correcta: String? = null,
    val min_caracteres: Int? = null,
    val max_caracteres: Int? = null
)

/**
 * Envoltura para el JSON que esperamos de la IA en preguntas mixtas.
 */
@Serializable
data class MixedQuestionsEnvelope(
    val preguntas: List<MixedGeneratedQuestionDto>
)

/**
 * Servicio que llama a la API de OpenAI para generar preguntas de entrevista / test
 * y también feedback genérico de texto.
 */
class InterviewQuestionService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {

    private val openAiUrl = "https://api.openai.com/v1/chat/completions"
    private val json = Json { ignoreUnknownKeys = true }

    // ========================================================
    // 1) Función original: solo preguntas abiertas (entrevista)
    // ========================================================

    suspend fun generateQuestionsForJob(
        job: JobNormalizedDto,
        cantidad: Int = 3
    ): List<String> {
        val prompt = buildPrompt(job, cantidad)

        val requestBody = OpenAIChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(
                    role = "system",
                    content = """
                        Eres un generador de preguntas para ENTREVISTAS LABORALES.
                        
                        Tu objetivo es crear preguntas de entrevista en español, enfocadas en evaluar
                        si la persona encaja con el cargo descrito en el aviso de trabajo.

                        Reglas:
                        - Genera exactamente $cantidad preguntas de entrevista laboral.
                        - Las preguntas deben poder usarse en una entrevista real (no tipo prueba de alternativa).
                        - Mezcla evaluación técnica y de experiencia/habilidades blandas, según el aviso.
                        - No incluyas respuestas, solo las preguntas.
                        - No expliques nada fuera del JSON.

                        Responde SOLO con un JSON de la forma:
                        {
                          "preguntas": [
                            "pregunta 1",
                            "pregunta 2",
                            "pregunta 3"
                          ]
                        }
                    """.trimIndent()
                ),
                OpenAIChatMessage(
                    role = "user",
                    content = prompt
                )
            )
        )

        println("🧠 Generando $cantidad preguntas abiertas para aviso: ${job.titulo} (${job.empresa ?: "N/A"})")

        val response: OpenAIChatResponse = httpClient.post(openAiUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        val content = response.choices.firstOrNull()?.message?.content
            ?: return emptyList()

        val preguntas = parseQuestionsFromContent(content, cantidad)

        println("✅ OpenAI devolvió ${preguntas.size} preguntas abiertas para '${job.titulo}'")

        return preguntas
    }

    private fun buildPrompt(job: JobNormalizedDto, cantidad: Int): String =
        """
        Genera $cantidad preguntas de entrevista en español para este aviso laboral.

        Título: ${job.titulo}
        Empresa: ${job.empresa ?: "No especificada"}
        Ubicación: ${job.ubicacion ?: "No especificada"}

        Descripción:
        ${job.descripcion}
        """.trimIndent()

    private fun parseQuestionsFromContent(
        content: String,
        cantidad: Int
    ): List<String> {
        val cleaned = cleanJsonMarkdown(content)

        return try {
            val root: JsonElement = json.parseToJsonElement(cleaned)
            val preguntasJson = root.jsonObject["preguntas"]?.jsonArray
                ?: return emptyList()

            preguntasJson
                .mapNotNull { el ->
                    el.toString()
                        .trim('"')
                        .takeIf { it.isNotBlank() }
                }
                .take(cantidad)
        } catch (e: Exception) {
            println("⚠️ No se pudo parsear JSON de OpenAI, devolviendo texto completo como una sola pregunta")
            listOf(cleaned)
        }
    }

    /**
     * Limpia fences tipo ```json ... ``` para quedarnos solo con el JSON.
     */
    private fun cleanJsonMarkdown(text: String): String {
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')

        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1)
        }

        return text.trim()
    }

    // ===========================================================
    // 2) NUEVO: Preguntas mixtas (cerradas + abiertas) para IAJOB
    // ===========================================================

    /**
     * Genera preguntas mixtas: mayoría de "seleccion_unica" y minoría "abierta_texto".
     *
     * Se usa para el flujo de banco de preguntas en la tabla `pregunta`
     * con tipo_banco = 'IAJOB'.
     */
    suspend fun generateMixedQuestionsForJob(
        job: JobNormalizedDto,
        cantidad: Int = 3
    ): List<MixedGeneratedQuestionDto> {

        val prompt = """
            Estás generando preguntas para practicar ENTREVISTAS PROFESIONALES
            en el sector de tecnología, basadas en un aviso real de trabajo.
            
            Aviso:
            Título: ${job.titulo}
            Empresa: ${job.empresa ?: "N/A"}
            Ubicación: ${job.ubicacion ?: "N/A"}
            Descripción:
            ${job.descripcion.take(1500)}
            
            Debes generar EXACTAMENTE $cantidad preguntas en español.
            
            REGLAS IMPORTANTES:
            - Usa una mezcla de:
              * preguntas CERRADAS de selección única (tipo "seleccion_unica")
              * preguntas ABIERTAS de desarrollo (tipo "abierta_texto")
            - La MAYORÍA de las preguntas deben ser CERRADAS (seleccion_unica)
              y la MINORÍA ABIERTAS (abierta_texto).
              Ejemplos:
                - Si pides 3: 2 cerradas, 1 abierta.
                - Si pides 5: 4 cerradas, 1 abierta (o 3 cerradas, 2 abiertas).
            - Todas deben ser técnicas o de experiencia ligada al rol (no preguntas genéricas tipo "¿Cuál es tu mayor debilidad?").
            - Deben servir para evaluar conocimientos y experiencia relacionados con el aviso.
            
            ESTRUCTURA DE CADA PREGUNTA:
            
            Campo "tipo":
              - "seleccion_unica" para preguntas de alternativa.
              - "abierta_texto" para preguntas abiertas.
            
            Campo "nivel":
              - "jr", "medio" o "sr" según la complejidad de la pregunta.
            
            Campo "enunciado":
              - El texto de la pregunta.
            
            Campo "pistas":
              - Lista de strings que den pistas sobre la respuesta correcta o elementos clave a considerar.
              - Ejemplo: ["Describe lo que el sistema debe hacer", "Comportamiento"].
            
            Campo "opciones" (solo si tipo = "seleccion_unica"):
              - Lista de opciones con:
                { "id": "A", "texto": "..." }
              - Deben ser entre 3 y 5 opciones razonables.
            
            Campo "respuesta_correcta" (solo si tipo = "seleccion_unica"):
              - Debe ser el ID de la opción correcta, por ejemplo "B".
            
            Campo "min_caracteres" y "max_caracteres" (solo si tipo = "abierta_texto"):
              - Usa típicamente min 20, max 300 para respuestas abiertas.
            
            RESPUESTA:
            Devuelve EXCLUSIVAMENTE un JSON con el siguiente formato:
            
            {
              "preguntas": [
                {
                  "tipo": "seleccion_unica",
                  "nivel": "jr",
                  "enunciado": "¿...?",
                  "pistas": ["..."],
                  "opciones": [
                    { "id": "A", "texto": "..." },
                    { "id": "B", "texto": "..." },
                    { "id": "C", "texto": "..." }
                  ],
                  "respuesta_correcta": "B",
                  "min_caracteres": null,
                  "max_caracteres": null
                },
                {
                  "tipo": "abierta_texto",
                  "nivel": "jr",
                  "enunciado": "¿...?",
                  "pistas": ["..."],
                  "opciones": [],
                  "respuesta_correcta": null,
                  "min_caracteres": 20,
                  "max_caracteres": 300
                }
              ]
            }
        """.trimIndent()

        val requestBody = OpenAIChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(
                    role = "system",
                    content = "Eres un experto creando bancos de preguntas técnicas para entrevistas laborales de TI."
                ),
                OpenAIChatMessage(
                    role = "user",
                    content = prompt
                )
            ),
            temperature = 0.6
        )

        println("🧠 Generando $cantidad preguntas MIXTAS para aviso: ${job.titulo} (${job.empresa ?: "N/A"})")

        val response: OpenAIChatResponse = httpClient.post(openAiUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        val content = response.choices.firstOrNull()?.message?.content ?: return emptyList()

        println("🤖 OpenAI Raw Mixed Content: $content")

        return parseMixedQuestions(content)
    }

    private fun parseMixedQuestions(content: String): List<MixedGeneratedQuestionDto> {
        val cleaned = cleanJsonMarkdown(content)
        return try {
            val root = json.parseToJsonElement(cleaned).jsonObject
            val preguntasJson = root["preguntas"]?.jsonArray ?: return emptyList()

            preguntasJson.map {
                json.decodeFromJsonElement(MixedGeneratedQuestionDto.serializer(), it)
            }
        } catch (e: Exception) {
            println("⚠️ Error parseando preguntas mixtas: ${e.message}")
            println("⚠️ Contenido limpiado: $cleaned")
            emptyList()
        }
    }

    // ===========================================================
    // 3) Versión para TEST DE NIVELACIÓN (lo que usa tu ruta)
    // ===========================================================

    suspend fun generateMultipleChoiceQuestions(
        job: JobNormalizedDto,
        cantidad: Int = 5
    ): List<GeneratedQuestionDto> {
        val prompt = """
            Genera $cantidad preguntas de selección múltiple (multiple choice) para una prueba técnica de nivelación,
            basadas en este aviso de trabajo:

            Título: ${job.titulo}
            Empresa: ${job.empresa ?: "N/A"}
            Descripción: ${job.descripcion.take(1000)}... (truncado)

            FORMATO ESTRICTO DEL JSON DE RESPUESTA:

            {
              "preguntas": [
                {
                  "enunciado": "¿Pregunta?",
                  "opciones": ["Opción A", "Opción B", "Opción C", "Opción D"],
                  "respuestaCorrecta": 0,
                  "explicacion": "Por qué es correcta la opción seleccionada...",
                  "dificultad": 2
                }
              ]
            }

            DONDE:
            - "respuestaCorrecta" es el índice (0-based) dentro del arreglo "opciones".
            - "dificultad" es:
                1 = básico,
                2 = intermedio,
                3 = avanzado.
            - Todas las preguntas deben ser técnicas y coherentes con el aviso.
            - No incluyas ningún texto fuera del JSON.
        """.trimIndent()

        val requestBody = OpenAIChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(role = "system", content = "Eres un experto técnico creando tests de nivelación."),
                OpenAIChatMessage(role = "user", content = prompt)
            ),
            temperature = 0.7
        )

        println("🧠 Generando $cantidad preguntas de selección múltiple para NIVELACIÓN: ${job.titulo}")

        val response: OpenAIChatResponse = httpClient.post(openAiUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        val content = response.choices.firstOrNull()?.message?.content ?: return emptyList()

        println("🤖 OpenAI Raw MC Content: $content")

        return parseMultipleChoiceQuestions(content)
    }

    private fun parseMultipleChoiceQuestions(content: String): List<GeneratedQuestionDto> {
        val cleaned = cleanJsonMarkdown(content)
        return try {
            val root = json.parseToJsonElement(cleaned).jsonObject
            val preguntas = root["preguntas"]?.jsonArray ?: return emptyList()

            preguntas.map {
                json.decodeFromJsonElement(GeneratedQuestionDto.serializer(), it)
            }
        } catch (e: Exception) {
            println("⚠️ Error parseando preguntas de selección múltiple: ${e.message}")
            println("⚠️ Contenido limpiado: $cleaned")
            emptyList()
        }
    }

    // ===========================================================
    // 4) Método genérico para generar TEXTO (feedback, etc.)
    // ===========================================================

    /**
     * Método genérico para pedirle a OpenAI que genere texto a partir de un prompt.
     * Se usa, por ejemplo, para feedback de pruebas prácticas.
     */
    suspend fun generarTexto(prompt: String): String {
        val requestBody = OpenAIChatRequest(
            model = "gpt-4o-mini",
            messages = listOf(
                OpenAIChatMessage(
                    role = "system",
                    content = """
                        Eres un asistente experto en feedback para entrevistas técnicas de TI.
                        Siempre respondes en español, con un tono claro, profesional y motivador.
                    """.trimIndent()
                ),
                OpenAIChatMessage(
                    role = "user",
                    content = prompt
                )
            ),
            temperature = 0.6
        )

        val response: OpenAIChatResponse = httpClient.post(openAiUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        return response.choices.firstOrNull()?.message?.content?.trim()
            ?: "No se pudo generar feedback automático en este momento. Intenta nuevamente más tarde."
    }
}
