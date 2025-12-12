/* src/main/kotlin/services/JSearchService.kt */

package services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ================== DTOs que reflejan la respuesta de JSearch ==================

@Serializable
data class JSearchHighlights(
    @SerialName("Responsibilities")
    val responsibilities: List<String>? = null,
    @SerialName("Qualifications")
    val qualifications: List<String>? = null,
    @SerialName("Skills")
    val skills: List<String>? = null
)

@Serializable
data class JSearchJob(
    @SerialName("job_id")
    val jobId: String,
    @SerialName("job_title")
    val jobTitle: String,
    @SerialName("employer_name")
    val employerName: String? = null,
    @SerialName("job_city")
    val jobCity: String? = null,
    @SerialName("job_country")
    val jobCountry: String? = null,
    @SerialName("job_description")
    val jobDescription: String? = null,
    @SerialName("job_highlights")
    val jobHighlights: JSearchHighlights? = null
)

@Serializable
data class JSearchResponse(
    val status: String? = null,
    val data: List<JSearchJob> = emptyList()
)

// ================== DTO normalizado para tu app ==================

@Serializable
data class JobNormalizedDto(
    val idExterno: String,
    val fuente: String = "jsearch",
    val titulo: String,
    val empresa: String? = null,
    val ubicacion: String? = null,
    val descripcion: String,
    val responsabilidades: List<String> = emptyList(),
    val requisitos: List<String> = emptyList(),
    val habilidades: List<String> = emptyList()
)

fun JSearchJob.toNormalized(): JobNormalizedDto =
    JobNormalizedDto(
        idExterno = jobId,
        titulo = jobTitle,
        empresa = employerName,
        ubicacion = listOfNotNull(jobCity, jobCountry)
            .joinToString(", ")
            .ifBlank { null },
        descripcion = jobDescription ?: "",
        responsabilidades = jobHighlights?.responsibilities ?: emptyList(),
        requisitos = jobHighlights?.qualifications ?: emptyList(),
        habilidades = jobHighlights?.skills ?: emptyList()
    )

// ================== Servicio ==================

class JSearchService(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val apiHost: String
) {
    private val baseUrl = "https://jsearch.p.rapidapi.com"

    suspend fun searchJobs(
        query: String,
        country: String? = null,
        page: Int = 1
    ): List<JobNormalizedDto> {
        val response: JSearchResponse = httpClient.get("$baseUrl/search") {
            header("X-RapidAPI-Key", apiKey)
            header("X-RapidAPI-Host", apiHost)
            parameter("query", query)
            parameter("page", page)
            parameter("num_pages", 1)
            parameter("date_posted", "all")
            if (!country.isNullOrBlank()) {
                parameter("country", country)
            }
        }.body()

        return response.data.map { it.toNormalized() }
    }
}
