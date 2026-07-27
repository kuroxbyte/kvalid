plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Integración OPCIONAL con Spring. Módulo aparte: el core nunca depende de un framework.
dependencies {
    api(project(":kvalid-runtime"))
    implementation(libs.spring.web)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
