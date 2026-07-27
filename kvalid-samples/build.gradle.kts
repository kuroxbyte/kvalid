plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    application
}

kotlin { jvmToolchain(17) }

// Ejemplos EJECUTABLES de kvalid en un solo módulo:
//  - fuentes Kotlin (`src/main/kotlin`) → variante KSP (extensión `Type.validate()`)
//  - fuentes Java   (`src/main/java`)   → variante APT (estática `TypeValidator.validate(obj)`)
//  - ejemplos de INTEGRACIÓN: Ktor (StatusPages) y Spring (@RestControllerAdvice), i18n.
// No se publica. `./gradlew :kvalid-samples:run` ejecuta la demo de consola.
dependencies {
    implementation(project(":kvalid-annotations"))
    implementation(project(":kvalid-runtime"))
    implementation(project(":kvalid-i18n"))

    // Integraciones (opcionales en producción; aquí para los ejemplos).
    implementation(project(":kvalid-ktor"))
    implementation(project(":kvalid-spring"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.spring.web)

    // Frontend Kotlin (KSP) sobre las fuentes .kt; frontend Java (APT) sobre las .java.
    ksp(project(":kvalid-processor"))
    annotationProcessor(project(":kvalid-apt"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

application {
    mainClass.set("dev.kvalid.samples.MainKt")
}

tasks.test { useJUnitPlatform() }
