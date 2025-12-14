/* src/main/kotlin/services/PracticeGlobalFeedbackService.kt */

package services

class PracticeGlobalFeedbackService(
    private val interviewQuestionService: InterviewQuestionService
) {

    suspend fun generarFeedbackGeneral(
        puntaje: Int,
        totalPreguntas: Int,
        correctas: Int,
        preguntas: List<ResultadoPreguntaResConTexto>
    ): String {

        // 1) Separar correctas e incorrectas
        val incorrectas = preguntas.filter { !it.correcta }
        val correctasLista = preguntas.filter { it.correcta }

        // 2) Inferir “tema” aproximado según el texto de la pregunta (heurística simple)
        fun inferirTema(texto: String): String {
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

        val temasFuertes = correctasLista
            .map { inferirTema(it.textoPregunta) }
            .distinct()

        val temasDebiles = incorrectas
            .map { inferirTema(it.textoPregunta) }
            .distinct()

        val estadoBase = when {
            puntaje >= 80 ->
                "El candidato muestra un muy buen nivel para una entrevista técnica inicial."
            puntaje >= 60 ->
                "El candidato tiene una base razonable, pero todavía no está completamente listo para una entrevista técnica exigente."
            puntaje >= 40 ->
                "El candidato tiene varias brechas importantes y aún no está preparado para una entrevista técnica real."
            else ->
                "El candidato se encuentra en un nivel inicial y necesita trabajar bastante antes de enfrentar una entrevista técnica."
        }

        val resumenBase = "Puntaje: $puntaje%. Respuestas correctas: $correctas de $totalPreguntas."

        val fortalezasBase =
            if (temasFuertes.isNotEmpty()) {
                temasFuertes.joinToString("\n") { "- $it" }
            } else {
                "No se detectan áreas claramente dominadas; el desempeño fue parejo o el número de aciertos fue bajo."
            }

        val debilidadesBase =
            if (temasDebiles.isNotEmpty()) {
                temasDebiles.joinToString("\n") { "- $it" }
            } else {
                "No se detectan áreas críticas con errores recurrentes."
            }

        val resumenPreguntas = preguntas.joinToString("\n") { p ->
            val estado = if (p.correcta) "CORRECTA" else "INCORRECTA"
            val tipo = if (p.tipo == "abierta") "abierta" else "opción múltiple"
            val respuesta = p.respuestaUsuario ?: "(sin respuesta)"
            "- [$estado] ($tipo) Pregunta: \"${p.textoPregunta}\" | Respuesta usuario: \"$respuesta\""
        }

        val prompt = """
            Actúas como una APLICACIÓN ESPECIALIZADA en simulación de ENTREVISTAS TÉCNICAS para TI.
            Tu objetivo es dar feedback honesto, claro y accionable para que la persona se prepare
            para una entrevista real.

            Siempre respondes en español, en segunda persona ("tú") y con un tono profesional pero cercano
            y motivador. No repitas literalmente los textos de este prompt.

            DATOS GENERALES DEL RESULTADO:
            - $estadoBase
            - $resumenBase

            FORTALEZAS DETECTADAS:
            $fortalezasBase

            ÁREAS DÉBILES DETECTADAS:
            $debilidadesBase

            DETALLE POR PREGUNTA:
            $resumenPreguntas

            Con todo esto, genera un FEEDBACK COMPLETO con la siguiente estructura (no enumeres los puntos, solo respeta el orden y separa en párrafos claros):

            1) Diagnóstico general:
               - ¿Qué tan preparado está para una entrevista técnica real según este resultado?
               - ¿En qué rango dirías que está (por ejemplo: nivel inicial, base razonable, listo para entrevistas, etc.)?

            2) Puntos fuertes:
               - Explica en qué cosas va bien y cómo eso le ayuda en una entrevista real.
               - Refuerza lo positivo sin exagerar.

            3) Áreas a mejorar:
               - Menciona temas concretos que debería estudiar (por ejemplo: requisitos funcionales vs no funcionales, UML, técnicas de levantamiento de información, metodologías ágiles, calidad de software, etc.).
               - Relaciona estas áreas con posibles preguntas o situaciones de entrevistas.

            4) Plan de acción recomendado:
               - Explica qué podría hacer en los próximos días/semanas para mejorar (por ejemplo: estudiar teoría, hacer ejercicios, practicar explicando en voz alta, simular entrevistas, etc.).
               - Da sugerencias específicas y prácticas, no solo “estudia más”.

            5) Cierre motivador:
               - Termina con un mensaje corto que anime a la persona a seguir practicando y recuerde que esta prueba es un entrenamiento, no un juicio definitivo.

            No incluyas encabezados tipo "Diagnóstico general:", simplemente organiza el texto en párrafos naturales siguiendo esa estructura.
        """.trimIndent()

        return interviewQuestionService.generarTexto(prompt)
    }

    /**
     * Feedback determinístico "NLP" para cuentas estándar (sin llamada a LLM).
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
