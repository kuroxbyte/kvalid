plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.kover)
}

/**
 * Xcode hace falta para **enlazar** los ejecutables de test de Apple, no para compilar sus
 * `.klib`: con solo las Command Line Tools, `assemble` —y por tanto la publicación— funciona
 * igual. Sin Xcode se desactivan **únicamente los tests** de esos targets, para que
 * `./gradlew build` sea utilizable en un Mac recién montado.
 *
 * Los tests de Apple no se pierden: el job `apple` de CI corre en un runner de macOS, que sí
 * trae Xcode.
 */
val isMac: Boolean = System.getProperty("os.name").startsWith("Mac")

/** Tareas que EJECUTAN o ENLAZAN tests de un target Apple (las únicas que exigen Xcode). */
fun isAppleTestTask(taskName: String): Boolean {
    val appleTargets = listOf("macosX64", "macosArm64", "iosArm64", "iosX64", "iosSimulatorArm64")
    return appleTargets.any { target ->
        val cap = target.replaceFirstChar(Char::uppercaseChar)
        taskName == "${target}Test" ||
            taskName == "${target}TestBinaries" ||
            (taskName.startsWith("link") && taskName.contains("Test") && taskName.endsWith(cap))
    }
}

val xcodeAvailable: Boolean = isMac && runCatching {
    providers.exec {
        commandLine("/usr/bin/xcrun", "--find", "xcodebuild")
        isIgnoreExitValue = true
    }.result.get().exitValue == 0
}.getOrDefault(false)

if (isMac && !xcodeAvailable) {
    logger.lifecycle(
        "[kvalid] Xcode no está disponible (xcode-select apunta a las Command Line Tools): " +
            "se omiten los TESTS de los targets Apple. Compilación, assemble y publicación no " +
            "se ven afectados. Para ejecutarlos en local: instala Xcode y " +
            "`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`.",
    )
}

subprojects {
    group = "io.github.kuroxbyte"
    version = "0.4.0"
    // Kover en cada módulo para exponer la variante de cobertura que agrega el root.
    apply(plugin = "org.jetbrains.kotlinx.kover")

    if (!xcodeAvailable) {
        // `onlyIf` y no `enabled`: se evalúa al ejecutar, así que ninguna configuración
        // posterior del plugin de Kotlin lo puede deshacer.
        tasks.configureEach {
            if (isAppleTestTask(name)) {
                logger.info("[kvalid] sin Xcode → se omite ${project.path}:$name")
                onlyIf("Xcode no está disponible en esta máquina") { false }
            }
        }
    }
}

// Cobertura agregada (Kover) → XML en formato JaCoCo para SonarQube.
// Se incluyen los módulos de librería (no benchmarks ni samples, que no son código de producción).
dependencies {
    kover(project(":kvalid-annotations"))
    kover(project(":kvalid-runtime"))
    kover(project(":kvalid-core"))
    kover(project(":kvalid-processor"))
    kover(project(":kvalid-i18n"))
    kover(project(":kvalid-ktor"))
    kover(project(":kvalid-spring"))
    kover(project(":kvalid-spring-boot-starter"))
    kover(project(":kvalid-apt"))
}

// Publicación (vanniktech maven-publish, mismo patrón que kspkit). Se publican SOLO los módulos
// de librería — no benchmarks, samples, integration-tests ni incremental-tests. `kvalid-core`,
// `kvalid-apt` y `kvalid-processor` dependen de kspkit publicado (io.github.kuroxbyte:kspkit-*).
// Orden de release: kspkit primero, luego kvalid.
val publishedModules = mapOf(
    "kvalid-annotations" to "KValid annotations (KMP): @Validated + the constraints, zero deps",
    "kvalid-runtime" to "KValid runtime (KMP): ValidationResult, Violation, ValidationContext (zero framework deps)",
    "kvalid-core" to "KValid core: the validation domain (ClassModel -> ValidationModel), zero compiler deps",
    "kvalid-processor" to "KValid KSP2 processor: add with ksp(...) to generate Type.validate() at compile time",
    "kvalid-apt" to "KValid Java variant (javac APT): @Validated Java classes -> XValidator.validate(obj)",
    "kvalid-i18n" to "KValid i18n (KMP): MessageResolver — resolve code+params to text (optional)",
    "kvalid-ktor" to "KValid Ktor integration: StatusPages.kvalid() -> 400 with the violations (optional)",
    "kvalid-spring" to "KValid Spring integration: Validator SPI for native @Valid (MVC + WebFlux) and @RestControllerAdvice -> 400 (optional)",
    "kvalid-spring-boot-starter" to "KValid Spring Boot starter: auto-configuration wiring @Valid for Spring MVC and WebFlux",
)

configure(subprojects.filter { it.name in publishedModules.keys }) {
    apply(plugin = "com.vanniktech.maven.publish")
    extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        coordinates("io.github.kuroxbyte", project.name, version.toString())
        publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
        // Firma SOLO cuando existen las llaves (release); mavenLocal no la exige.
        if (providers.gradleProperty("signingInMemoryKey").isPresent ||
            providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent
        ) {
            signAllPublications()
        }
        pom {
            name.set(project.name)
            description.set(publishedModules.getValue(project.name))
            url.set("https://github.com/kuroxbyte/kvalid")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("kuroxbyte")
                    name.set("kuroxbyte")
                }
            }
            scm {
                url.set("https://github.com/kuroxbyte/kvalid")
                connection.set("scm:git:git://github.com/kuroxbyte/kvalid.git")
                developerConnection.set("scm:git:ssh://git@github.com/kuroxbyte/kvalid.git")
            }
        }
    }
}
