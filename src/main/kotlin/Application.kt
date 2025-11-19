package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import com.example.configureMonitoring

import plugins.configureSerialization
import plugins.configureStatusPages
import plugins.configureDatabase
import plugins.configureCORS
import plugins.DatabaseFactory

import security.configureSecurity
import routes.configureRouting

import kotlinx.serialization.json.Json
import data.repository.admin.PreguntaRepository
import data.repository.admin.AdminUserRepository
// ❌ Antes:
// import data.repository.auth.RecoveryCodeRepository
// ✅ Ahora:
import data.repository.usuarios.PasswordResetRepository

import services.EmailService
import io.github.cdimascio.dotenv.dotenv

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // Orden importante
    configureCORS()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()   // esto inicializa AuthCtx

    // --- crea repos (UNA sola vez) y pásalos al routing ---
    val db = DatabaseFactory.db
    val json = Json { ignoreUnknownKeys = true }

    val preguntaRepo  = PreguntaRepository(db, json)
    val adminUserRepo = AdminUserRepository(db)

    // ❌ Antes:
    // val recoveryCodeRepo = RecoveryCodeRepository(db)
    // ✅ Ahora usamos el repo nuevo de reset de contraseña:
    val recoveryCodeRepo = PasswordResetRepository()

    // Configurar EmailService con variables de entorno
    val dotenv = dotenv {
        ignoreIfMissing = true
    }

    val emailService = EmailService(
        smtpHost = dotenv["SMTP_HOST"] ?: "smtp.gmail.com",
        smtpPort = dotenv["SMTP_PORT"]?.toIntOrNull() ?: 465,
        username = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado"),
        password = dotenv["GMAIL_APP_PASSWORD"] ?: throw RuntimeException("GMAIL_APP_PASSWORD no configurado"),
        fromEmail = dotenv["GMAIL_USER"] ?: throw RuntimeException("GMAIL_USER no configurado")
    )

    // 👇 ahora configureRouting recibe PasswordResetRepository
    configureRouting(
        preguntaRepo = preguntaRepo,
        adminUserRepo = adminUserRepo,
        recoveryCodeRepo = recoveryCodeRepo,
        emailService = emailService,
        db = db
    )

    configureMonitoring()
}
