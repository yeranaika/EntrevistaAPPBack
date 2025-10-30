// src/main/kotlin/Application.kt
package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import kotlinx.coroutines.runBlocking

// plugins
import plugins.configureSettings
import plugins.configureDatabase
import plugins.configureSerialization
import plugins.configureStatusPages

// routes
import routes.configureRouting

// security/monitoring (según tu estructura, ambos viven en el package `security`)
import security.configureSecurity
import com.example.configureMonitoring

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSettings()

    // ⬇️ SI tu configureDatabase() es suspend (como lo hicimos con retry)
    runBlocking { configureDatabase() }
    // ⬇️ SI NO es suspend, usa esto y borra el runBlocking de arriba:
    // configureDatabase()

    configureSerialization()
    configureStatusPages()
    configureSecurity()
    configureMonitoring()
    configureRouting()
}
