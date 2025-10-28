package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

// importa desde el package 'plugins'
import plugins.configureSerialization
import plugins.configureStatusPages
import plugins.configureDatabase
import plugins.configureSecurity
import com.example.configureMonitoring
import com.example.configureRouting

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity()
    configureMonitoring()
    configureRouting()
}
