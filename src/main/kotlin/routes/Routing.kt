package routes

import data.repository.admin.PreguntaRepository
import data.repository.admin.PruebaRepository
import data.repository.admin.AdminUserRepository
import data.repository.auth.RecoveryCodeRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import data.repository.soporte.TicketRepositoryImpl
import data.repository.billing.SuscripcionRepository
import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
import com.example.routes.intentosRoutes
import routes.cuestionario.prueba.pruebaRoutes
import routes.soporte.ticketRoutes
import routes.admin.adminPlanRoutes
import routes.billing.billingRoutes   // solo UNA import

import plugins.settings   // config de tu app
import plugins.DatabaseFactory
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier
import security.billing.GooglePlayBillingService
import services.EmailService
import org.jetbrains.exposed.sql.Database

fun Application.configureRouting(
    preguntaRepo: PreguntaRepository,
    adminUserRepo: AdminUserRepository,
    recoveryCodeRepo: RecoveryCodeRepository,
    emailService: EmailService,
    db: Database
) {
    // Instancias de repos
    val users = UserRepository()
    val profiles = ProfileRepository()
    val consentRepo = ConsentimientoRepository()
    val pruebaRepo = PruebaRepository(DatabaseFactory.db)
    val ticketRepo = TicketRepositoryImpl(db)
    val suscripcionRepo = SuscripcionRepository()
    val onboardingRepo = OnboardingRepository()

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
        meRoutes(users, profiles)

        // Consentimientos  👉 versión que tú tenías funcionando
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

        // Admin: crear pruebas
        AdminPruebaRoutes(pruebaRepo)

        // Admin: crear usuarios (incluye admins)
        AdminUserCreateRoutes(adminUserRepo)

        // Admin: gestión completa de usuarios (listar, actualizar rol, eliminar)
        adminRoutes(adminUserRepo)

        // Soporte: tickets de usuarios
        ticketRoutes(ticketRepo)
    }
}
