package routes

import data.repository.admin.PreguntaRepository
import data.repository.admin.PruebaRepository
import data.repository.admin.AdminUserRepository
import data.repository.usuarios.PasswordResetRepository

import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import data.repository.billing.SuscripcionRepository
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.ObjetivoCarreraRepository
import data.repository.cuestionario.PlanPracticaRepository
import data.repository.nivelacion.PreguntaNivelacionRepository
import data.repository.nivelacion.TestNivelacionRepository
import data.repository.usuarios.RecordatorioPreferenciaRepository
import data.repository.jobs.JobRequisitoRepository
import data.repository.requisitos_cargo.SkillsCargoRepository

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

// Rutas existentes
import routes.auth.authRoutes
import routes.auth.googleAuthRoutes
import routes.auth.passwordRecoveryRoutes
import routes.me.meRoutes
import routes.consent.ConsentRoutes
import routes.auth.profileRoutes
import data.repository.AppAndroid.OnboardingRepository
import routes.admin.adminPreguntaRoutes
import routes.admin.AdminPruebaRoutes
import routes.admin.AdminUserCreateRoutes
import routes.admin.adminRoutes
import routes.admin.adminPreguntaNivelacionRoutes
import com.example.routes.intentosRoutes
import routes.cuestionario.prueba_practica.pruebaRoutes
import routes.admin.adminPlanRoutes
import routes.billing.billingRoutes
import routes.usuario.recordatorios.recordatorioRoutes
import routes.sesiones.sesionesRoutes
import routes.auth.deleteAccountRoute
import routes.cuestionario.planPracticaRoutes
import routes.nivelacion.testNivelacionRoutes
import routes.historial.historialRoutes
import routes.onboarding.onboardingRoutes
import routes.requisitos_cargo.jobsSkillsRoutes

import plugins.settings
import plugins.DatabaseFactory
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier
import security.billing.GooglePlayBillingService
import services.EmailService
import org.jetbrains.exposed.sql.Database

// Cliente HTTP para servicios externos
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// API JOB (JSearch) + OpenAI
import services.JSearchService
import services.InterviewQuestionService
import routes.jobs.jobsRoutes
import routes.jobs.jobsGeneratorRoutes
import routes.jobs.jobsRequirementsRoutes
import routes.jobs.jobsRequirementsBulkRoutes
import routes.cuestionario.prueba_practica.pruebaFrontRoutes
import routes.cuestionario.respuesta_practica.pruebaPracticaRespuestaRoutes

import data.repository.admin.InformeGestionRepository
import routes.admin.informeGestionRoutes

fun Application.configureRouting(
    preguntaRepo: PreguntaRepository,
    adminUserRepo: AdminUserRepository,
    recoveryCodeRepo: PasswordResetRepository,
    emailService: EmailService,
    db: Database
) {
    // Instancias de repos
    val users = UserRepository()
    val profiles = ProfileRepository()
    val consentRepo = ConsentimientoRepository()
    val pruebaRepo = PruebaRepository(DatabaseFactory.db)
    val suscripcionRepo = SuscripcionRepository()
    val onboardingRepo = OnboardingRepository()
    val recordatorioRepo = RecordatorioPreferenciaRepository()
    val objetivos = ObjetivoCarreraRepository()
    val planRepo = PlanPracticaRepository()
    val preguntaNivelacionRepo = PreguntaNivelacionRepository()
    val testNivelacionRepo = TestNivelacionRepository()
    val jobRequisitoRepo = JobRequisitoRepository()
    val skillsCargoRepository = SkillsCargoRepository()
    val informeRepo = InformeGestionRepository()

    // Contexto JWT
    val ctx: AuthCtx = if (attributes.contains(AuthCtxKey)) {
        attributes[AuthCtxKey]
    } else {
        throw IllegalStateException(
            "AuthCtx no disponible. Asegúrate de llamar primero a security.configureSecurity() en Application.module()."
        )
    }

    val s = settings()

    val usuariosOAuthRepository = UsuariosOAuthRepositoryImpl()
    val googleTokenVerifier = GoogleTokenVerifier(s.googleClientId)

    val billingService = GooglePlayBillingService(
        userRepo = users,
        suscripcionRepo = suscripcionRepo,
        packageName = s.googlePlayPackage,
        serviceAccountJsonBase64 = s.googlePlayServiceJsonBase64,
        useMock = s.googlePlayBillingMock
    )

    // HTTP client compartido
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                }
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000   // hasta 60s para toda la request
            connectTimeoutMillis = 10_000   // 10s para conectar
            socketTimeoutMillis  = 60_000   // 60s sin datos en el socket
        }

        expectSuccess = true
    }

    // Servicios externos
    val jSearchService = JSearchService(
        httpClient = httpClient,
        apiKey = s.jSearchApiKey,
        apiHost = s.jSearchApiHost,
    )

    val interviewQuestionService = InterviewQuestionService(
        httpClient = httpClient,
        apiKey = s.openAiApiKey,
    )

    routing {
        // Healthcheck
        get("/health") { call.respondText("OK") }

        // Auth
        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)

        // Google OAuth2
        googleAuthRoutes(
            repo = usuariosOAuthRepository,
            verifier = googleTokenVerifier
        )

        // Billing
        billingRoutes(
            billingService = billingService,
            suscripcionRepo = suscripcionRepo
        )

        // Password Recovery
        passwordRecoveryRoutes(recoveryCodeRepo, emailService, db, usuariosOAuthRepository)

        // /me y /me/perfil
        meRoutes(users, profiles, objetivos)

        // Consentimientos
        ConsentRoutes(
            consentRepo = consentRepo,
            consentTextRepo = ConsentTextRepository()
        )

        profileRoutes(onboardingRepo)
        adminPlanRoutes(onboardingRepo)

        // Intentos de prueba
        intentosRoutes()

        // Rutas de cuestionario
        pruebaRoutes()

        // Front de pruebas (nivelación/práctica para app/web)
        pruebaFrontRoutes()

        // Admin: banco de preguntas
        adminPreguntaRoutes(preguntaRepo)

        // Recordatorios
        recordatorioRoutes(recordatorioRepo)

        // Sesiones de entrevista tipo chat
        sesionesRoutes()

        // Admin: crear pruebas
        AdminPruebaRoutes(pruebaRepo)

        // Admin: crear usuarios
        AdminUserCreateRoutes(adminUserRepo)

        // Admin: gestión de usuarios
        adminRoutes(adminUserRepo)

        // Eliminar cuenta
        deleteAccountRoute(users)

        // Rutas jobs (JSearch + OpenAI)
        jobsRoutes(
            jSearchService = jSearchService,
            interviewQuestionService = interviewQuestionService
        )

        jobsGeneratorRoutes(
            jSearchService = jSearchService,
            interviewQuestionService = interviewQuestionService
        )

        jobsRequirementsRoutes(
            jSearchService = jSearchService,
            jobRequisitoRepository = jobRequisitoRepo
        )

        jobsRequirementsBulkRoutes(
            jSearchService = jSearchService,
            jobRequisitoRepository = jobRequisitoRepo
        )

        // Plan de práctica
        planPracticaRoutes(planRepo, profiles, objetivos, testNivelacionRepo)

        // Skills por cargo
        jobsSkillsRoutes(skillsCargoRepository)

        // ✅ Tests de nivelación (solo 2 parámetros, como está declarada la función)
        testNivelacionRoutes(
            preguntaRepo = preguntaNivelacionRepo,
            testRepo = testNivelacionRepo
        )

        // Admin: preguntas de nivelación
        adminPreguntaNivelacionRoutes(preguntaNivelacionRepo)

        informeGestionRoutes(informeRepo)

        // Historial unificado (Tests + Entrevistas)
        val sesionRepo = data.repository.sesiones.SesionEntrevistaRepository()
        historialRoutes(sesionRepo, testNivelacionRepo)

        // Onboarding
        onboardingRoutes(profiles, objetivos)
    }
}
