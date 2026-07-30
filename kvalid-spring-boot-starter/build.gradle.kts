plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Starter de Spring Boot: capa fina de auto-configuración sobre kvalid-spring (que no conoce
// Boot). Convención de terceros: <lib>-spring-boot-starter (spring-boot-starter-* es de Spring).
dependencies {
    api(project(":kvalid-spring"))
    // El starter es "pilas incluidas": trae los mensajes por defecto para que un 400 no
    // salga con `"notBlank"` sin configurar nada. kvalid-i18n no arrastra dependencias.
    api(project(":kvalid-i18n"))
    api(libs.spring.boot.autoconfigure)
    // El binding por constructor de @ConfigurationProperties en Kotlin lo resuelve Boot con
    // KotlinReflectionParameterNameDiscoverer: sin kotlin-reflect el arranque falla con
    // NoClassDefFoundError. Aquí llegaba transitivamente SOLO al classpath de test, así que
    // los tests pasaban y el usuario se lo habría comido.
    implementation(libs.kotlin.reflect)

    // El starter sirve a servlet Y a reactivo: cada bloque de configuración se activa por
    // @ConditionalOnClass, así que no se arrastra el stack contrario al consumidor.
    compileOnly(libs.spring.webmvc)
    compileOnly(libs.spring.webflux)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.webflux)
    // Para el test de coexistencia: Hibernate Validator (Jakarta) junto a kvalid.
    testImplementation(libs.spring.boot.starter.validation)
    // Jackson necesita el módulo Kotlin para deserializar data classes sin no-arg ctor.
    testImplementation(libs.jackson.module.kotlin)
}

tasks.test { useJUnitPlatform() }
