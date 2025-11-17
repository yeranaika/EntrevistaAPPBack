package routes

import data.repository.admin.PreguntaRepository
import data.repository.admin.AdminUserRepository
import data.repository.usuarios.ProfileRepository
import data.repository.usuarios.UserRepository

import data.repository.usuarios.UsuariosOAuthRepositoryImpl
import data.repository.billing.SuscripcionRepository

import data.repository.usuarios.ConsentTextRepository
import data.repository.usuarios.ConsentimientoRepository

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

import routes.auth.authRoutes
import routes.auth.googleAuthRoutes
import routes.me.meRoutes
import routes.consent.ConsentRoutes

import routes.admin.adminPreguntaRoutes
import routes.admin.AdminUserCreateRoutes
import com.example.routes.intentosRoutes
import routes.billing.billingRoutes

import plugins.settings   // config de tu app
import security.AuthCtx
import security.AuthCtxKey
import security.auth.GoogleTokenVerifier
import security.billing.GooglePlayBillingService

fun Application.configureRouting(
    preguntaRepo: PreguntaRepository,
    adminUserRepo: AdminUserRepository
) {
    // Instancias de repos
    val users = UserRepository()
    val profiles = ProfileRepository()
    val consentRepo = ConsentimientoRepository()
    val suscripcionRepo = SuscripcionRepository()

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

    // 👉 Repositorio OAuth para Google (el que pegaste tú: UsuariosOAuthRepositoryImpl)
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

        // /me y /me/perfil (GET/PUT)
        meRoutes(users, profiles)

        // Consentimientos
        ConsentRoutes(
            consentRepo = consentRepo,
            consentTextRepo = ConsentTextRepository()
        )

        // Intentos de prueba
        intentosRoutes()

        // Admin: banco de preguntas
        adminPreguntaRoutes(preguntaRepo)

        // Admin: crear usuarios (incluye admins)
        AdminUserCreateRoutes(adminUserRepo)
    }
}
