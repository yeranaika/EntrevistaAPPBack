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
import kotlinx.serialization.json.jsonPrimitive

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
 * Servicio que llama a la API de OpenAI para generar preguntas de entrevista
 * a partir de un aviso laboral (JobNormalizedDto).
 */
class InterviewQuestionService(
    private val httpClient: HttpClient,
    private val apiKey: String
) {

    private val openAiUrl = "https://api.openai.com/v1/chat/completions"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateQuestionsForJob(
        job: JobNormalizedDto,
        cantidad: Int = 3
    ): List<String> {
        val prompt = buildPrompt(job, cantidad)

        val requestBody = OpenAIChatRequest(
            model = "gpt-4o-mini", // ajusta al modelo que tengas habilitado
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

        println("🧠 Generando $cantidad preguntas para aviso: ${job.titulo} (${job.empresa ?: "N/A"})")

        val response: OpenAIChatResponse = httpClient.post(openAiUrl) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body()

        val content = response.choices.firstOrNull()?.message?.content
            ?: return emptyList()

        val preguntas = parseQuestionsFromContent(content, cantidad)

        println("✅ OpenAI devolvió ${preguntas.size} preguntas para '${job.titulo}'")

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
        // A veces OpenAI responde con ```json ... ```
        val cleaned = cleanJsonMarkdown(content)

        return try {
            val root: JsonElement = json.parseToJsonElement(cleaned)
            val preguntasJson = root.jsonObject["preguntas"]?.jsonArray
                ?: return emptyList()

            preguntasJson
                .mapNotNull { el ->
                    val raw = el.jsonPrimitive.content
                    raw.takeIf { it.isNotBlank() }
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
        val trimmed = text.trim()
        if (trimmed.startsWith("```")) {
            return trimmed
                .lineSequence()
                .drop(1) // saltar la línea ```json o ```
                .takeWhile { !it.startsWith("```") }
                .joinToString("\n")
                .trim()
        }
        return trimmed
    }
}
