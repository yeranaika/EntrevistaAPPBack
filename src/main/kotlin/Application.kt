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

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // Orden importante: security primero para que ponga AuthCtx en attributes
    // (en la práctica: antes de montar las rutas; mantenemos security antes de configureRouting)
    configureCORS()                           // ← configurar CORS antes que todo
    configureSerialization()
    configureStatusPages()
    configureDatabase()                       // ← inicializa DatabaseFactory.db
    configureSecurity()                       // ← esto debe poblar AuthCtxKey

    // --- crea repos (UNA sola vez) y pásalos al routing ---
    val db = DatabaseFactory.db
    val json = Json { ignoreUnknownKeys = true }

    val preguntaRepo  = PreguntaRepository(db, json)
    val adminUserRepo = AdminUserRepository(db)

    configureRouting(preguntaRepo, adminUserRepo)   // ← routing recibe 2 repos
    configureMonitoring()
}
