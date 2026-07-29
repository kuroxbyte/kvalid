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
    // spring-context trae org.springframework.validation.* (Validator/SmartValidator/Errors),
    // el SPI que alimenta @Valid en MVC y WebFlux por igual.
    api(libs.spring.context)
    implementation(libs.spring.web)
    // Un solo artefacto sirve a servlet y a reactivo: las clases de configuración solo se usan
    // bajo @ConditionalOnClass, así que NO deben arrastrar el stack contrario al consumidor.
    compileOnly(libs.spring.webmvc)
    compileOnly(libs.spring.webflux)

    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.webflux)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
