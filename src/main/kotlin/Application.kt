package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

// importa desde el package 'plugins'
import plugins.configureSerialization
import plugins.configureStatusPages
import com.example.configureMonitoring
import plugins.configureDatabase
import security.configureSecurity

import routes.configureRouting

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()
    configureMonitoring()
    configureRouting()
}
