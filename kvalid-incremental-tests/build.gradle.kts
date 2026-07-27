plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(17) }

// Incrementalidad de KSP: kctfork compila todo de cero, así que este módulo arma con Gradle
// TestKit un consumidor REAL que hace `includeBuild` de este repo, compila dos veces y verifica
// que tocar una clase no relacionada NO regenera `<Type>Validator.kt`.
dependencies {
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("kvalid.rootDir", rootDir.absolutePath)
    systemProperty("kvalid.kotlinVersion", libs.versions.kotlin.get())
    systemProperty("kvalid.kspVersion", libs.versions.ksp.get())
}
