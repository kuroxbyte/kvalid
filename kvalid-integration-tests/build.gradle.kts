plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin { jvmToolchain(17) }

// Consumidor REAL end-to-end: aplica KSP sobre clases `@Validated` propias y los tests llaman
// al `validate()` generado DIRECTAMENTE (sin reflexión). Complementa a los compile-tests
// (kctfork) del procesador, que cubren los casos NEGATIVOS (constraint mal aplicada → error).
dependencies {
    implementation(project(":kvalid-annotations"))
    implementation(project(":kvalid-runtime"))
    implementation(project(":kvalid-i18n"))
    ksp(project(":kvalid-processor"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
