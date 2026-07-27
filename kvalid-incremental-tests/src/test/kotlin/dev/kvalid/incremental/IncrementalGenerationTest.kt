package dev.kvalid.incremental

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Incrementalidad de KSP vía Gradle TestKit: arma un consumidor real que hace `includeBuild` de
 * este repo, compila dos veces y verifica que tocar una clase NO relacionada no regenera
 * `<Type>Validator.kt`. Es la prueba en Gradle real del aislamiento por delegación.
 */
class IncrementalGenerationTest {

    @TempDir
    lateinit var projectDir: File

    private val root: String = System.getProperty("kvalid.rootDir")
    private val kotlinVersion: String = System.getProperty("kvalid.kotlinVersion")
    private val kspVersion: String = System.getProperty("kvalid.kspVersion")

    private fun file(path: String): File = File(projectDir, path).apply { parentFile.mkdirs() }

    @BeforeEach
    fun scaffold() {
        file("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { gradlePluginPortal(); mavenCentral(); google() }
            }
            dependencyResolutionManagement {
                repositories { mavenCentral(); google() }
            }
            rootProject.name = "kvalid-incremental-consumer"
            includeBuild("${root.replace("\\", "\\\\")}") {
                dependencySubstitution {
                    substitute(module("io.github.kuroxbyte:kvalid-annotations")).using(project(":kvalid-annotations"))
                    substitute(module("io.github.kuroxbyte:kvalid-runtime")).using(project(":kvalid-runtime"))
                    substitute(module("io.github.kuroxbyte:kvalid-processor")).using(project(":kvalid-processor"))
                }
            }
            """.trimIndent(),
        )
        file("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "$kotlinVersion"
                id("com.google.devtools.ksp") version "$kspVersion"
            }
            kotlin { jvmToolchain(17) }
            dependencies {
                implementation("io.github.kuroxbyte:kvalid-annotations:0.1.0-SNAPSHOT")
                implementation("io.github.kuroxbyte:kvalid-runtime:0.1.0-SNAPSHOT")
                ksp("io.github.kuroxbyte:kvalid-processor:0.1.0-SNAPSHOT")
            }
            """.trimIndent(),
        )
        file("gradle.properties").writeText(
            """
            ksp.useKSP2=true
            ksp.incremental=true
            org.gradle.jvmargs=-Xmx2g
            """.trimIndent(),
        )
        file("src/main/kotlin/sample/User.kt").writeText(
            """
            package sample
            import dev.kvalid.annotations.NotBlank
            import dev.kvalid.annotations.Validated

            @Validated
            data class User(@NotBlank val name: String)
            """.trimIndent(),
        )
        file("src/main/kotlin/sample/Unrelated.kt").writeText(
            """
            package sample

            data class Unrelated(val x: Int)
            """.trimIndent(),
        )
    }

    private fun runKsp() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("kspKotlin", "--stacktrace")
            .forwardOutput()
            .build()
    }

    @Test
    fun `tocar una clase no relacionada no regenera el archivo de validacion`() {
        runKsp()
        val generated = File(projectDir, "build/generated/ksp/main/kotlin/sample/UserValidator.kt")
        assertTrue(generated.exists(), "UserValidator.kt debió generarse en el primer build")
        val firstStamp = generated.lastModified()

        Thread.sleep(1_100) // resolución de mtime del filesystem
        file("src/main/kotlin/sample/Unrelated.kt").writeText(
            """
            package sample

            data class Unrelated(val x: Int, val y: Int)
            """.trimIndent(),
        )
        runKsp()
        assertEquals(
            firstStamp, generated.lastModified(),
            "UserValidator.kt fue regenerado al tocar una clase no relacionada (incrementalidad rota)",
        )

        // Control (el assert anterior no es vacuo): tocar la clase anotada SÍ regenera.
        Thread.sleep(1_100)
        file("src/main/kotlin/sample/User.kt").writeText(
            """
            package sample
            import dev.kvalid.annotations.NotBlank
            import dev.kvalid.annotations.Size
            import dev.kvalid.annotations.Validated

            @Validated
            data class User(@NotBlank @Size(max = 20) val name: String)
            """.trimIndent(),
        )
        runKsp()
        assertNotEquals(
            firstStamp, generated.lastModified(),
            "el control falló: tocar la clase anotada debía regenerar UserValidator.kt",
        )
    }
}
