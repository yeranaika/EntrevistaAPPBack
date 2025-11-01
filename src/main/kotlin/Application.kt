package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

import plugins.configureSerialization
import plugins.configureStatusPages
import plugins.configureDatabase
import security.configureSecurity
import routes.configureRouting

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    // Orden importante: security primero para que ponga AuthCtx en attributes
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()     // <- esto debe poblar AuthCtxKey
    configureRouting()      // <- ya puede leer AuthCtx de attributes
}
