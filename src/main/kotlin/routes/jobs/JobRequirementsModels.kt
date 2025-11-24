package routes.jobs

import kotlinx.serialization.Serializable

@Serializable
data class JobRequirementsReq(
    val cargo: String,
    val area: String? = null,
    val country: String = "Chile",
    val limitAvisos: Int = 10,
    val skillsLimit: Int = 10
)

@Serializable
data class JobRequirementItem(
    val fuenteTitulo: String?,
    val empresa: String?,
    val ubicacion: String?,
    val nivelInferido: String,
    val requisitosTecnicos: List<String>,
    val requisitosBlandos: List<String>,
    val urlAviso: String?
)

@Serializable
data class JobRequirementsResponse(
    val cargo: String,
    val area: String?,
    val totalAvisosAnalizados: Int,
    val avisosUsados: Int,
    val maxRequisitosPorTipo: Int,
    val items: List<JobRequirementItem>
)

// Helper functions
fun extractRequirementsFromJob(job: services.JobNormalizedDto): Pair<List<String>, List<String>> {
    val descripcion = job.descripcion ?: ""

    // Lista simple de palabras clave técnicas comunes
    val tecnicasKeywords = listOf(
        "java", "python", "javascript", "kotlin", "react", "angular", "vue",
        "sql", "nosql", "mongodb", "postgresql", "mysql", "aws", "azure",
        "docker", "kubernetes", "git", "ci/cd", "api", "rest", "graphql"
    )

    // Lista de soft skills comunes
    val blandasKeywords = listOf(
        "comunicación", "liderazgo", "trabajo en equipo", "problem solving",
        "pensamiento crítico", "adaptabilidad", "creatividad", "organización"
    )

    val tecnicas = mutableListOf<String>()
    val blandas = mutableListOf<String>()

    val descLower = descripcion.lowercase()

    tecnicasKeywords.forEach { keyword ->
        if (descLower.contains(keyword.lowercase())) {
            tecnicas.add(keyword)
        }
    }

    blandasKeywords.forEach { keyword ->
        if (descLower.contains(keyword.lowercase())) {
            blandas.add(keyword)
        }
    }

    return Pair(tecnicas.distinct(), blandas.distinct())
}

fun inferNivelFromTitleReq(titulo: String): String {
    val tituloLower = titulo.lowercase()
    return when {
        tituloLower.contains("senior") || tituloLower.contains("sr") -> "senior"
        tituloLower.contains("junior") || tituloLower.contains("jr") -> "junior"
        tituloLower.contains("trainee") || tituloLower.contains("practicante") -> "trainee"
        else -> "mid"
    }
}
