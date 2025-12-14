/* src/main/kotlin/services/PracticeGlobalFeedbackService.kt */

package services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeedbackGeneralV2(
    val version: String = "1.0",
    val summary: FeedbackSummary,
    val sections: List<FeedbackSection>
)

@Serializable
data class FeedbackSummary(
    val scorePercent: Int,
    val correct: Int,
    val total: Int,
    val readiness: String, // "listo_para_entrevista_inicial" | "base_razonable" | "brechas_importantes" | "nivel_inicial"
    val oneLiner: String
)

@Serializable
data class FeedbackSection(
    val key: String,   // "diagnostico" | "fortalezas" | "mejoras" | "plan" | "cierre"
    val title: String,
    val bullets: List<String>
)

class PracticeGlobalFeedbackService(
    private val interviewQuestionService: InterviewQuestionService
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun inferirTema(texto: String): String {
        val t = texto.lowercase()

        return when {
            "diagrama de flujo" in t || "flujo" in t ->
                "diagramas de flujo y simbología básica (decisión, inicio/fin, procesos)"

            "uml" in t ->
                "UML y sus diagramas principales (casos de uso, clases, etc.)"

            "requisito funcional" in t || "no funcional" in t ->
                "requisitos funcionales vs no funcionales y ejemplos de cada uno"

            "stakeholder" in t ->
                "identificación y rol de los stakeholders en proyectos de software"

            "levantamiento de información" in t || "entrevista" in t || "requisitos" in t ->
                "técnicas de levantamiento de requisitos (entrevistas, encuestas, talleres, etc.)"

            "bug" in t || "error" in t || "fallo" in t ->
                "conceptos básicos de calidad de software (bugs, defectos, incidencias)"

            "product owner" in t || "scrum" in t || "ágil" in t ->
                "metodologías ágiles y roles (Product Owner, Scrum Master, equipo de desarrollo)"

            "login" in t || "caso de uso" in t ->
                "modelado de casos de uso y actores principales"

            else ->
                "fundamentos de análisis y modelado de sistemas para el rol de Analista TI"
        }
    }

    private fun readinessFromScore(puntaje: Int): String = when {
        puntaje >= 80 -> "listo_para_entrevista_inicial"
        puntaje >= 60 -> "base_razonable"
        puntaje >= 40 -> "brechas_importantes"
        else -> "nivel_inicial"
    }

    private fun buildLegacyTextFromV2(v2: FeedbackGeneralV2): String {
        val sb = StringBuilder()
        sb.appendLine(v2.summary.oneLiner)
        sb.appendLine()
        v2.sections.forEach { sec ->
            sb.appendLine("${sec.title}:")
            sec.bullets.forEach { b -> sb.appendLine("- $b") }
            sb.appendLine()
        }
        return sb.toString().trim()
    }

    /**
     * NUEVO: Genera feedback general ESTRUCTURADO (JSON) usando IA.
     * Esto permite evitar el "churrullo" y mantener un formato consistente y renderizable.
     */
    suspend fun generarFeedbackGeneralV2(
        puntaje: Int,
        totalPreguntas: Int,
        correctas: Int,
        preguntas: List<ResultadoPreguntaResConTexto>
    ): FeedbackGeneralV2 {

        // 1) Separar correctas e incorrectas
        val incorrectas = preguntas.filter { !it.correcta }
        val correctasLista = preguntas.filter { it.correcta }

        // 2) Inferir temas fuertes/débiles desde el texto (heurística simple)
        val temasFuertes = correctasLista
            .map { inferirTema(it.textoPregunta) }
            .distinct()

        val temasDebiles = incorrectas
            .map { inferirTema(it.textoPregunta) }
            .distinct()

        val readiness = readinessFromScore(puntaje)

        // Nota: evitamos incluir "detalle por pregunta" textual para que no se vuelva largo.
        // La IA debe devolver bullets cortos y secciones fijas, ideales para Android/historial.
        val prompt = """
Actúas como una APLICACIÓN ESPECIALIZADA en simulación de ENTREVISTAS TÉCNICAS para TI.
Devuelve SOLO un JSON válido (sin markdown, sin texto extra).

Tu salida DEBE seguir EXACTAMENTE este esquema:

{
  "version": "1.0",
  "summary": {
    "scorePercent": <int>,
    "correct": <int>,
    "total": <int>,
    "readiness": "<string>",
    "oneLiner": "<string>"
  },
  "sections": [
    { "key": "diagnostico", "title": "Diagnóstico general", "bullets": ["...", "..."] },
    { "key": "fortalezas", "title": "Puntos fuertes", "bullets": ["...", "..."] },
    { "key": "mejoras", "title": "Áreas a mejorar", "bullets": ["...", "..."] },
    { "key": "plan", "title": "Plan de acción", "bullets": ["...", "..."] },
    { "key": "cierre", "title": "Cierre", "bullets": ["..."] }
  ]
}

REGLAS:
- bullets: frases cortas, máximo 4 por sección.
- No incluyas saltos de línea dentro de bullets.
- Usa español y segunda persona ("tú").
- oneLiner: 1 frase.
- "fortalezas" y "mejoras" deben referenciar (si aplica) estos temas (sin inventar otros ajenos).

DATOS:
- scorePercent: $puntaje
- correct: $correctas
- total: $totalPreguntas
- readiness: $readiness

TEMAS FUERTES (si aplica): ${temasFuertes.joinToString(" | ").ifBlank { "N/A" }}
TEMAS DÉBILES (si aplica): ${temasDebiles.joinToString(" | ").ifBlank { "N/A" }}
""".trimIndent()

        val raw = interviewQuestionService.generarTexto(prompt)

        return runCatching {
            json.decodeFromString<FeedbackGeneralV2>(raw)
        }.getOrElse {
            // Fallback seguro: si el modelo no entrega JSON válido, devolvemos estructura básica.
            FeedbackGeneralV2(
                summary = FeedbackSummary(
                    scorePercent = puntaje,
                    correct = correctas,
                    total = totalPreguntas,
                    readiness = readiness,
                    oneLiner = "Aquí tienes un resumen de tu desempeño y próximos pasos."
                ),
                sections = listOf(
                    FeedbackSection(
                        key = "diagnostico",
                        title = "Diagnóstico general",
                        bullets = listOf("Resultado: $puntaje% ($correctas/$totalPreguntas).")
                    ),
                    FeedbackSection(
                        key = "fortalezas",
                        title = "Puntos fuertes",
                        bullets = (if (temasFuertes.isNotEmpty()) temasFuertes else listOf("Buen desempeño general."))
                            .take(4)
                    ),
                    FeedbackSection(
                        key = "mejoras",
                        title = "Áreas a mejorar",
                        bullets = (if (temasDebiles.isNotEmpty()) temasDebiles else listOf("No se detectan brechas críticas."))
                            .take(4)
                    ),
                    FeedbackSection(
                        key = "plan",
                        title = "Plan de acción",
                        bullets = listOf(
                            "Repasa conceptos clave y practica respuestas en voz alta.",
                            "Simula entrevistas con preguntas mixtas para ganar fluidez."
                        )
                    ),
                    FeedbackSection(
                        key = "cierre",
                        title = "Cierre",
                        bullets = listOf("Sigue practicando: vas en buena dirección.")
                    )
                )
            )
        }
    }

    /**
     * Compatibilidad: mantiene la firma original.
     * En vez de generar un "churrullo", construye texto a partir del JSON estructurado (V2).
     * Nota: NO toca el NLP.
     */
    suspend fun generarFeedbackGeneral(
        puntaje: Int,
        totalPreguntas: Int,
        correctas: Int,
        preguntas: List<ResultadoPreguntaResConTexto>
    ): String {
        val v2 = generarFeedbackGeneralV2(
            puntaje = puntaje,
            totalPreguntas = totalPreguntas,
            correctas = correctas,
            preguntas = preguntas
        )
        return buildLegacyTextFromV2(v2)
    }

    /**
     * Feedback determinístico "NLP" para cuentas estándar (sin llamada a LLM).
     * NO MODIFICAR: se deja tal cual.
     */
    fun generarFeedbackNlpBasico(
        puntaje: Int,
        totalPreguntas: Int,
        correctas: Int,
        preguntas: List<ResultadoPreguntaResConTexto>
    ): String {
        val abiertas = preguntas.filter { it.tipo == "abierta" }
        val opcionMultiple = preguntas.filter { it.tipo != "abierta" }

        val tono = when {
            puntaje >= 80 -> "Tu desempeño es muy sólido; solo afina algunos detalles."
            puntaje >= 60 -> "Vas bien encaminado, pero aún hay brechas que cubrir."
            puntaje >= 40 -> "Se identifican lagunas importantes; prioriza los temas clave."
            else -> "Es un punto de partida: enfócate en fundamentos antes de una entrevista real."
        }

        val resumenAbiertas = abiertas.joinToString("\n") { p ->
            val estado = if (p.correcta) "✔" else "✘"
            "$estado Respuesta abierta: \"${p.textoPregunta.take(120)}\""
        }.ifBlank { "No hubo respuestas abiertas en esta prueba." }

        val resumenCerradas = opcionMultiple.joinToString("\n") { p ->
            val estado = if (p.correcta) "✔" else "✘"
            "$estado Opción múltiple: \"${p.textoPregunta.take(120)}\""
        }.ifBlank { "No hubo preguntas de opción múltiple." }

        val sugerencias = buildString {
            appendLine("- Prioriza las preguntas que contestaste mal y revisa definiciones básicas.")
            appendLine("- Practica explicando en voz alta tus respuestas para ganar fluidez.")
            appendLine("- Alterna ejercicios de opción múltiple con preguntas abiertas para equilibrar teoría y comunicación.")
        }

        return """
            Resultado: $puntaje% ($correctas/$totalPreguntas correctas)
            $tono

            📌 Resumen de abiertas:
            $resumenAbiertas

            📌 Resumen de opción múltiple:
            $resumenCerradas

            ▶️ Sugerencias inmediatas:
            $sugerencias
        """.trimIndent()
    }
}
