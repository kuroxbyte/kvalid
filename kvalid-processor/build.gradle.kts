plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// COMPOSICIÓN: kspkit-ksp + kvalid-core. Único módulo con SymbolProcessorProvider.
dependencies {
    implementation(libs.kspkit)
    implementation(libs.genkit.emit)      // puente FileSpec.toGeneratedFile()
    implementation(libs.kotlinpoet)       // emisor Kotlin (simétrico a JavaPoet)
    implementation(project(":kvalid-core"))
    compileOnly(project(":kvalid-annotations"))
    implementation(libs.ksp.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.kctfork.ksp)
    testImplementation(project(":kvalid-runtime"))
    testImplementation(project(":kvalid-annotations"))
    // Solo para los tests de codegen: el adaptador con componentModel=spring lleva
    // @Component, así que debe estar en el classpath para que el generado COMPILE.
    testImplementation(libs.spring.context)
    testImplementation(testFixtures(libs.genkit.ports))
}

tasks.test { useJUnitPlatform() }
