plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

// Variante Java: annotation processor (javac APT). Reutiliza kvalid-core y kspkit-apt;
// emite Java con JavaPoet (métodos estáticos).
dependencies {
    implementation(project(":kvalid-core"))
    implementation(libs.aptkit)
    implementation(libs.genkit.emit)   // puente JavaFile.toGeneratedFile()
    implementation(libs.javapoet)
    compileOnly(project(":kvalid-annotations"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(project(":kvalid-annotations"))
    testImplementation(project(":kvalid-runtime"))
    // El adaptador con componentModel=spring lleva @Component: debe estar en el classpath
    // para que el Java GENERADO compile de verdad en el test.
    testImplementation(libs.spring.context)
}

tasks.test { useJUnitPlatform() }
