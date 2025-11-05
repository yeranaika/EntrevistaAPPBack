// src/main/kotlin/Application.kt
package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
<<<<<<< HEAD
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
=======
import com.example.configureMonitoring

import plugins.configureSerialization
import plugins.configureStatusPages
import plugins.configureDatabase

import security.configureSecurity
import routes.configureRouting

import routes.consent.ConsentRoutes


>>>>>>> 9c708be58c0da2e0fdd1b19afce2e15bc84039ff

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
<<<<<<< HEAD
    configureSettings()

    // ⬇️ SI tu configureDatabase() es suspend (como lo hicimos con retry)
    runBlocking { configureDatabase() }
    // ⬇️ SI NO es suspend, usa esto y borra el runBlocking de arriba:
    // configureDatabase()

    configureSerialization()
    configureStatusPages()
    configureSecurity()
=======
    // Orden importante: security primero para que ponga AuthCtx en attributes
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()     // <- esto debe poblar AuthCtxKey
    configureRouting()      // <- ya puede leer AuthCtx de attributes
>>>>>>> 9c708be58c0da2e0fdd1b19afce2e15bc84039ff
    configureMonitoring()
}
