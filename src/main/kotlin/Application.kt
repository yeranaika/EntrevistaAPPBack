package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain  
import org.slf4j.event.Level                 

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    configureMonitoring() // instala CallLogging
    configureRouting()
}
