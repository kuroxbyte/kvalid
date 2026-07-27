plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Integración OPCIONAL con Ktor. Módulo aparte: el core nunca depende de un framework.
dependencies {
    api(project(":kvalid-runtime"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

tasks.test { useJUnitPlatform() }
