plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// DOMINIO. Solo modelo neutral + puertos. NI KSP NI KotlinPoet (emit produce texto).
dependencies {
    api(libs.genkit.model)
    api(libs.genkit.ports)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.konsist)
    testImplementation(testFixtures(libs.genkit.ports))
}

tasks.test { useJUnitPlatform() }
