package routes

import data.repository.admin.PreguntaRepository
import data.repository.admin.PruebaRepository
import data.repository.admin.AdminUserRepository
import data.repository.auth.RecoveryCodeRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository
import data.repository.usuarios.ConsentimientoRepository
import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import data.repository.soporte.TicketRepositoryImpl

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import routes.auth.authRoutes
import routes.auth.googleAuthRoutes
import routes.auth.passwordRecoveryRoutes
import routes.me.meRoutes
import routes.consent.ConsentRoutes
import routes.admin.AdminPreguntaCreateRoute
import routes.admin.AdminPruebaRoutes
import routes.admin.AdminUserCreateRoutes
import routes.admin.adminRoutes
import com.example.routes.intentosRoutes
import routes.cuestionario.prueba.pruebaRoutes
import routes.soporte.ticketRoutes

import plugins.settings   // ⬅ importante
import plugins.DatabaseFactory
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier   // ⬅️ usa este
import services.EmailService
import org.jetbrains.exposed.sql.Database

// Recibimos los repos por parámetro para no crearlos aquí
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
   
    // El contexto JWT debe haber sido cargado por configureSecurity()
    val ctx: AuthCtx = if (attributes.contains(AuthCtxKey)) {
        attributes[AuthCtxKey]
    } else {
        // Si llegas aquí, aún no se ejecutó configureSecurity()
        throw IllegalStateException(
            "AuthCtx no disponible. Asegúrate de llamar primero a security.configureSecurity() en Application.module()."
        )
    }

    // ⬇⬇⬇ toma también la config de Google desde Settings
    val s = settings()

    routing {
        // Healthcheck
        get("/health") { call.respondText("OK") }

        // Auth (register/login/refresh/reset)
        authRoutes(ctx.issuer, ctx.audience, ctx.algorithm)

        // Google OAuth2
        googleAuthRoutes(
            repo = UsuariosOAuthRepositoryImpl(),
            verifier = GoogleTokenVerifier(s.googleClientId)
        )

        // Password Recovery (forgot-password, reset-password)
        passwordRecoveryRoutes(recoveryCodeRepo, emailService, db)

        // /me y /me/perfil (GET/PUT)
        meRoutes(users, profiles)

        // Consentimientos
        ConsentRoutes(consentRepo)
        
        // Intentos de prueba
        intentosRoutes()

        // Rutas de cuestionario (pruebas, asociar preguntas, responder)
        pruebaRoutes()

        // Admin: banco de preguntas
        AdminPreguntaCreateRoute(preguntaRepo)

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
