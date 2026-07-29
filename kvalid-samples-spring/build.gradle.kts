plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)   // abre las clases @Component/@Configuration (Kotlin es final por defecto)
    alias(libs.plugins.ksp)
    application
}

kotlin { jvmToolchain(17) }

// App Spring Boot EJECUTABLE que demuestra @Valid nativo con las DOS variantes a la vez:
// el DTO Kotlin lo procesa KSP y el DTO Java lo procesa APT, en la misma aplicación.
// No se publica: es una muestra. Se arranca con `./gradlew :kvalid-samples-spring:run`.
dependencies {
    implementation(project(":kvalid-annotations"))
    implementation(project(":kvalid-runtime"))
    implementation(project(":kvalid-spring-boot-starter"))   // la auto-configuración

    ksp(project(":kvalid-processor"))                        // DTOs Kotlin
    annotationProcessor(project(":kvalid-apt"))              // DTOs Java

    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)               // deserializar data classes
    // Solo la ANOTACIÓN @Valid. Ojo: NO hace falta spring-boot-starter-validation —
    // Hibernate Validator es justo lo que KValid sustituye (validar sin reflexión).
    implementation(libs.jakarta.validation.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.starter.test)
}

// ── Lo único que hay que recordar: pedir el adaptador @Component en cada frontend ──────────
// Sin esto no se generan los KValidator y @Valid no valida nada (sin error visible).
ksp { arg("kvalid.componentModel", "spring") }

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Akvalid.componentModel=spring")
}

application { mainClass.set("dev.kvalid.samples.spring.SampleApplicationKt") }

tasks.test { useJUnitPlatform() }
