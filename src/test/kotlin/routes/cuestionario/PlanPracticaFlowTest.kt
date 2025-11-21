package routes.cuestionario

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Serializable
data class MeResponse(
    val id: String,
    val email: String,
    val nombre: String? = null,
    val idioma: String? = null,
    val perfil: PerfilResponse? = null,
    val meta: String? = null
)

@Serializable
data class PerfilResponse(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null
)

@Serializable
data class ObjetivoRequest(
    val nombreCargo: String,
    val sector: String? = null
)

@Serializable
data class ObjetivoResponse(
    val id: String,
    val nombreCargo: String,
    val sector: String?
)

@Serializable
data class PerfilRequest(
    val nivelExperiencia: String? = null,
    val area: String? = null,
    val pais: String? = null,
    val notaObjetivos: String? = null
)

@Serializable
data class PlanPracticaResponse(
    val id: String,
    val area: String?,
    val metaCargo: String?,
    val nivel: String?,
    val pasos: List<PasoResponse>
)

@Serializable
data class PasoResponse(
    val id: String,
    val orden: Int,
    val titulo: String,
    val descripcion: String?,
    val sesionesPorSemana: Int?
)

class PlanPracticaFlowTest {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun registerAndLogin(client: io.ktor.client.HttpClient): String {
        val email = "plan-test-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Plan Test User"
                }
            """.trimIndent())
        }

        val registerBody = registerResponse.bodyAsText()
        val tokens = json.decodeFromString<routes.auth.TokenResponse>(registerBody)
        return tokens.accessToken
    }

    @Test
    fun `test get me endpoint with new user`() = testApplication {
        val token = registerAndLogin(client)

        val response = client.get("/me") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.bodyAsText()
        val meResponse = json.decodeFromString<MeResponse>(body)

        assertNotNull(meResponse.id)
        assertNotNull(meResponse.email)
        // New user should not have perfil or meta yet
    }

    @Test
    fun `test get me without authentication fails`() = testApplication {
        val response = client.get("/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test create and update perfil`() = testApplication {
        val token = registerAndLogin(client)

        // Create perfil
        val createResponse = client.put("/me/perfil") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nivelExperiencia": "Tengo experiencia intermedia",
                    "area": "TI",
                    "pais": "AR",
                    "notaObjetivos": "Quiero mejorar mis habilidades técnicas"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)

        // Verify perfil was created
        val meResponse = client.get("/me") {
            bearerAuth(token)
        }

        val meBody = meResponse.bodyAsText()
        val me = json.decodeFromString<MeResponse>(meBody)

        assertNotNull(me.perfil)
        assertEquals("Tengo experiencia intermedia", me.perfil?.nivelExperiencia)
        assertEquals("TI", me.perfil?.area)
        assertEquals("AR", me.perfil?.pais)
    }

    @Test
    fun `test create and get objetivo`() = testApplication {
        val token = registerAndLogin(client)

        // Create objetivo
        val createResponse = client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "Senior Backend Developer",
                    "sector": "TI"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, createResponse.status)

        val createBody = createResponse.bodyAsText()
        val objetivo = json.decodeFromString<ObjetivoResponse>(createBody)

        assertNotNull(objetivo.id)
        assertEquals("Senior Backend Developer", objetivo.nombreCargo)
        assertEquals("TI", objetivo.sector)

        // Verify objetivo appears in /me
        val meResponse = client.get("/me") {
            bearerAuth(token)
        }

        val me = json.decodeFromString<MeResponse>(meResponse.bodyAsText())
        assertEquals("Senior Backend Developer", me.meta)

        // Get objetivo directly
        val getObjetivoResponse = client.get("/me/objetivo") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, getObjetivoResponse.status)

        val getObjetivo = json.decodeFromString<ObjetivoResponse>(getObjetivoResponse.bodyAsText())
        assertEquals("Senior Backend Developer", getObjetivo.nombreCargo)
    }

    @Test
    fun `test get objetivo when none exists returns 404`() = testApplication {
        val token = registerAndLogin(client)

        val response = client.get("/me/objetivo") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test create objetivo with empty name fails`() = testApplication {
        val token = registerAndLogin(client)

        val response = client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "",
                    "sector": "TI"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("nombre_cargo_requerido"))
    }

    @Test
    fun `test get plan practica without authentication fails`() = testApplication {
        val response = client.get("/plan-practica")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `test generate plan practica with perfil and objetivo`() = testApplication {
        val token = registerAndLogin(client)

        // Step 1: Create perfil
        client.put("/me/perfil") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nivelExperiencia": "Tengo experiencia intermedia",
                    "area": "TI",
                    "pais": "AR",
                    "notaObjetivos": "Quiero mejorar mis habilidades técnicas"
                }
            """.trimIndent())
        }

        // Step 2: Create objetivo
        client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "Senior Backend Developer",
                    "sector": "TI"
                }
            """.trimIndent())
        }

        // Step 3: Get plan practica
        val planResponse = client.get("/plan-practica") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, planResponse.status)

        val planBody = planResponse.bodyAsText()
        val plan = json.decodeFromString<PlanPracticaResponse>(planBody)

        assertNotNull(plan.id)
        assertEquals("TI", plan.area)
        assertEquals("Senior Backend Developer", plan.metaCargo)
        assertEquals("Tengo experiencia intermedia", plan.nivel)
        assertTrue(plan.pasos.isNotEmpty(), "Plan should have steps")

        // Verify steps are ordered
        plan.pasos.forEachIndexed { index, paso ->
            assertEquals(index + 1, paso.orden)
            assertNotNull(paso.id)
            assertTrue(paso.titulo.isNotEmpty())
        }
    }

    @Test
    fun `test plan practica is cached - same plan returned on second call`() = testApplication {
        val token = registerAndLogin(client)

        // Create perfil and objetivo
        client.put("/me/perfil") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nivelExperiencia": "Estoy empezando en este tema",
                    "area": "TI",
                    "pais": "US"
                }
            """.trimIndent())
        }

        client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "Junior Developer"
                }
            """.trimIndent())
        }

        // First call - generates plan
        val firstResponse = client.get("/plan-practica") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, firstResponse.status)
        val firstPlan = json.decodeFromString<PlanPracticaResponse>(firstResponse.bodyAsText())

        // Second call - should return same plan
        val secondResponse = client.get("/plan-practica") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, secondResponse.status)
        val secondPlan = json.decodeFromString<PlanPracticaResponse>(secondResponse.bodyAsText())

        // Should be the same plan
        assertEquals(firstPlan.id, secondPlan.id, "Should return same plan ID")
        assertEquals(firstPlan.pasos.size, secondPlan.pasos.size)
    }

    @Test
    fun `test plan practica generated without perfil uses defaults`() = testApplication {
        val token = registerAndLogin(client)

        // Don't create perfil or objetivo, just request plan
        val planResponse = client.get("/plan-practica") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, planResponse.status)

        val plan = json.decodeFromString<PlanPracticaResponse>(planResponse.bodyAsText())

        assertNotNull(plan.id)
        // Should use default values
        assertEquals("General", plan.area)
        assertEquals("Mejorar habilidades", plan.metaCargo)
        assertEquals("jr", plan.nivel)
        assertTrue(plan.pasos.isNotEmpty())
    }

    @Test
    fun `test complete flow - register, create perfil, objetivo, get plan`() = testApplication {
        val email = "complete-flow-${System.currentTimeMillis()}@example.com"
        val password = "TestPassword123!"

        // Step 1: Register
        val registerResponse = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "$email",
                    "password": "$password",
                    "nombre": "Complete Flow User",
                    "idioma": "es"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val registerBody = registerResponse.bodyAsText()
        val tokens = json.decodeFromString<routes.auth.TokenResponse>(registerBody)
        val token = tokens.accessToken

        // Step 2: Check initial /me
        val initialMeResponse = client.get("/me") {
            bearerAuth(token)
        }

        val initialMe = json.decodeFromString<MeResponse>(initialMeResponse.bodyAsText())
        assertEquals(email, initialMe.email)
        assertEquals("Complete Flow User", initialMe.nombre)
        assertEquals("es", initialMe.idioma)

        // Step 3: Create perfil
        val perfilResponse = client.put("/me/perfil") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nivelExperiencia": "Tengo mucha experiencia",
                    "area": "TI",
                    "pais": "MX",
                    "notaObjetivos": "Busco un rol de liderazgo técnico"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, perfilResponse.status)

        // Step 4: Create objetivo
        val objetivoResponse = client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "Tech Lead",
                    "sector": "TI"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, objetivoResponse.status)

        // Step 5: Check /me again
        val updatedMeResponse = client.get("/me") {
            bearerAuth(token)
        }

        val updatedMe = json.decodeFromString<MeResponse>(updatedMeResponse.bodyAsText())
        assertNotNull(updatedMe.perfil)
        assertEquals("TI", updatedMe.perfil?.area)
        assertEquals("Tech Lead", updatedMe.meta)

        // Step 6: Generate plan
        val planResponse = client.get("/plan-practica") {
            bearerAuth(token)
        }

        assertEquals(HttpStatusCode.OK, planResponse.status)

        val plan = json.decodeFromString<PlanPracticaResponse>(planResponse.bodyAsText())
        assertNotNull(plan.id)
        assertEquals("TI", plan.area)
        assertEquals("Tech Lead", plan.metaCargo)
        assertEquals("Tengo mucha experiencia", plan.nivel)
        assertTrue(plan.pasos.size >= 3, "Plan should have at least 3 steps")

        // Advanced users should get extra steps
        assertTrue(plan.pasos.size >= 4, "Experienced users should get more steps")
    }

    @Test
    fun `test delete objetivo`() = testApplication {
        val token = registerAndLogin(client)

        // Create objetivo
        client.put("/me/objetivo") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "nombreCargo": "Software Engineer"
                }
            """.trimIndent())
        }

        // Verify it exists
        val getResponse = client.get("/me/objetivo") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // Delete it
        val deleteResponse = client.delete("/me/objetivo") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        // Verify it's gone
        val getAfterDelete = client.get("/me/objetivo") {
            bearerAuth(token)
        }
        assertEquals(HttpStatusCode.NotFound, getAfterDelete.status)
    }
}
