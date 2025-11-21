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
import routes.cuestionario.prueba.pruebaRoutes
import routes.admin.adminPlanRoutes
import routes.billing.billingRoutes

import data.repository.usuarios.RecordatorioPreferenciaRepository
import routes.usuario.recordatorios.recordatorioRoutes
import routes.sesiones.sesionesRoutes
import routes.cuestionario.planPracticaRoutes
import routes.nivelacion.testNivelacionRoutes
import routes.historial.historialRoutes

import plugins.settings
import plugins.DatabaseFactory
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier
import security.billing.GooglePlayBillingService
import services.EmailService
import org.jetbrains.exposed.sql.Database

// 👉 Cliente HTTP para servicios externos
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

// API JOB (JSearch) + OpenAI
import routes.jobs.jobsRoutes
import services.JSearchService
import services.InterviewQuestionService
import routes.jobs.jobsGeneratorRoutes 

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

    // El contexto JWT debe haber sido cargado por configureSecurity()
    val ctx: AuthCtx = if (attributes.contains(AuthCtxKey)) {
        attributes[AuthCtxKey]
    } else {
        throw IllegalStateException(
            "AuthCtx no disponible. Asegúrate de llamar primero a security.configureSecurity() en Application.module()."
        )
    }

    // Config general de la app (ya la usas para Billing)
    val s = settings()

    // 👉 Repositorio OAuth para Google
    val usuariosOAuthRepository = UsuariosOAuthRepositoryImpl()

    val googleTokenVerifier = GoogleTokenVerifier(s.googleClientId)

    // Servicio de Billing (Google Play) usando los repos ya creados
    val billingService = GooglePlayBillingService(
        userRepo = users,
        suscripcionRepo = suscripcionRepo,
        packageName = s.googlePlayPackage,
        serviceAccountJsonBase64 = s.googlePlayServiceJsonBase64,
        useMock = s.googlePlayBillingMock
    )

    // ============================
    //   CLIENTE HTTP COMPARTIDO
    // ============================
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
        expectSuccess = true
    }

    // ============================
    //   SERVICIOS EXTERNOS
    // ============================
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

        // Auth (register/login/refresh/reset)
        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)

        // 🔹 Google OAuth2 (web + móvil)
        googleAuthRoutes(
            repo = usuariosOAuthRepository,
            verifier = googleTokenVerifier
        )

        // Billing (Google Play)
        billingRoutes(
            billingService = billingService,
            suscripcionRepo = suscripcionRepo
        )

        // Password Recovery (forgot-password, reset-password)
        passwordRecoveryRoutes(recoveryCodeRepo, emailService, db)

        // /me y /me/perfil (GET/PUT)
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

        // Rutas de cuestionario (pruebas, asociar preguntas, responder)
        pruebaRoutes()

        // Admin: banco de preguntas
        adminPreguntaRoutes(preguntaRepo)

        // Recordatorios
        recordatorioRoutes(recordatorioRepo)

        // Sesiones de entrevista tipo chat
        sesionesRoutes()

        // Admin: crear pruebas
        AdminPruebaRoutes(pruebaRepo)

        // Admin: crear usuarios (incluye admins)
        AdminUserCreateRoutes(adminUserRepo)

        // Admin: gestión completa de usuarios (listar, actualizar rol, eliminar)
        adminRoutes(adminUserRepo)

        // ============================
        //   RUTAS DE JOBS (JSEARCH + OPENAI)
        // ============================
        jobsRoutes(
            jSearchService = jSearchService,
            interviewQuestionService = interviewQuestionService
        )

        // Ruta independiente para generar y guardar preguntas en la tabla pregunta
        jobsGeneratorRoutes(
            jSearchService = jSearchService,
            interviewQuestionService = interviewQuestionService
        )

        // Plan de práctica
        planPracticaRoutes(planRepo, profiles, objetivos)

        // Tests de nivelación
        testNivelacionRoutes(preguntaNivelacionRepo, testNivelacionRepo)

        // Admin: preguntas de nivelación
        adminPreguntaNivelacionRoutes(preguntaNivelacionRepo)

        // Historial unificado (Tests + Entrevistas)
        val sesionRepo = data.repository.sesiones.SesionEntrevistaRepository()
        historialRoutes(sesionRepo, testNivelacionRepo)
    }
}
