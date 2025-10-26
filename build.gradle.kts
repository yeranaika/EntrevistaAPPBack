import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.example"
version = "0.0.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21)) // 🔹 Fuerza Java 21
    }
}

kotlin {
    jvmToolchain(21) // 🔹 Fuerza Kotlin a compilar para JVM 21
}


application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)

    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.config.yaml)

    // Logging
    implementation(libs.logback.classic)

    // Tests
    testImplementation(libs.ktor.server.test.host) // alias correcto
    testImplementation(libs.kotlin.test.junit)
}
