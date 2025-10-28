import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.JavaExec

plugins {
    application
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

    // DB
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.55.0")
    implementation("org.postgresql:postgresql:42.7.4")

    // Seguridad: hash de contraseñas en la app 
    implementation("de.mkammerer:argon2-jvm:2.11")                     // Argon2id (reemplaza contraseña en claro)

    // JWT
    implementation("com.auth0:java-jwt:4.4.0")

    // Logs
    implementation("ch.qos.logback:logback-classic:1.5.6")

    //
    implementation("io.ktor:ktor-server-status-pages-jvm:3.0.0")
    implementation("io.ktor:ktor-server-auth-jvm:3.0.0")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.0.0")
}

// tareas clave secreta
tasks.named<JavaExec>("run") {
    jvmArgs("-Dconfig.resource=secrets.conf")
}
