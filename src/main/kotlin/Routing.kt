package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

fun Application.configureRouting() {
    val log = LoggerFactory.getLogger("Application")

    routing {
        // Salud
        get("/") { call.respondText("Hello World!") }

    }
}