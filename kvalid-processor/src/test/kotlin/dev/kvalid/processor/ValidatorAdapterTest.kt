@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kvalid.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import com.tschuchort.compiletesting.kspSourcesDir
import dev.kvalid.runtime.ValidationResult
import dev.kvalid.runtime.spi.KValidator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El adaptador `KValidator<T>` (opción `kvalid.componentModel`) es lo que permite
 * resolver "el validador de ESTE tipo" en un borde runtime. Aquí se verifica que se emite
 * solo cuando se pide, que compila, y que delega en el `validate()` generado.
 */
class ValidatorAdapterTest {

    private val source = """
        package t
        import dev.kvalid.annotations.*

        @Validated
        data class Account(@NotBlank val name: String, @Min(18) val age: Int)
    """.trimIndent()

    private fun run(options: Map<String, String>): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = compilation(source, options)
        val result = compilation.compile()
        check(result.exitCode == KotlinCompilation.ExitCode.OK) { "Falló la compilación:\n${result.messages}" }
        return compilation to result
    }

    private fun KotlinCompilation.generatedFiles(): List<File> =
        kspSourcesDir.walkTopDown().filter { it.isFile }.toList()

    @Test
    fun `por defecto NO se genera adaptador`() {
        val (compilation, _) = run(emptyMap())
        val names = compilation.generatedFiles().map { it.name }
        assertTrue(names.any { it == "AccountValidator.kt" }, "falta el validate() de siempre: $names")
        assertFalse(names.any { it.contains("KValidator") }, "no debería haber adaptador: $names")
    }

    @Test
    fun `componentModel=spring genera adaptador anotado con @Component`() {
        val (compilation, _) = run(mapOf("kvalid.componentModel" to "spring"))

        val adapter = compilation.generatedFiles().firstOrNull { it.name == "AccountKValidator.kt" }
        assertNotNull(adapter, "no se generó el adaptador: ${compilation.generatedFiles().map { it.name }}")

        val code = adapter.readText()
        assertTrue("org.springframework.stereotype.Component" in code || "@Component" in code, code)
        assertTrue("KValidator<Account>" in code, code)
        assertTrue("value.validate()" in code, "el adaptador debe DELEGAR, no reimplementar:\n$code")
    }

    @Test
    fun `el adaptador generado funciona en runtime y delega en el validate generado`() {
        val (_, result) = run(mapOf("kvalid.componentModel" to "spring"))

        val adapterClass = result.classLoader.loadClass("t.AccountKValidator")
        val adapter = adapterClass.getDeclaredConstructor().newInstance() as KValidator<Any>
        val accountClass = result.classLoader.loadClass("t.Account")

        assertEquals(accountClass, adapter.type)

        val ok = accountClass.getDeclaredConstructor(String::class.java, Int::class.java).newInstance("Ana", 30)
        assertTrue(adapter.validate(ok) is ValidationResult.Valid)

        val bad = accountClass.getDeclaredConstructor(String::class.java, Int::class.java).newInstance("", 15)
        val violations = adapter.validate(bad).violationsOrEmpty()
        assertEquals(setOf("notBlank", "min"), violations.map { it.code }.toSet())
    }

    @Test
    fun `componentModel=serviceloader genera adaptador sin @Component y el META-INF-services`() {
        val (compilation, _) = run(mapOf("kvalid.componentModel" to "serviceloader"))
        val files = compilation.generatedFiles()

        val adapter = files.firstOrNull { it.name == "AccountKValidator.kt" }
        assertNotNull(adapter, "no se generó el adaptador: ${files.map { it.name }}")
        assertFalse("@Component" in adapter.readText(), "sin Spring no debe llevar @Component")

        val services = compilation.kspSourcesDir.parentFile
            .walkTopDown()
            .firstOrNull { it.isFile && it.name == "dev.kvalid.runtime.spi.KValidator" }
        assertNotNull(services, "falta META-INF/services: ${files.map { it.path }}")
        assertEquals("t.AccountKValidator", services.readText().trim())
    }

    /**
     * Regresión: en un módulo **mixto** Kotlin+Java, KSP también ve las clases Java. Si las
     * procesa, pasan dos cosas malas: genera un `validate()` **vacío** (no lee los constraints
     * de un record) y su adaptador **colisiona** con el que emite APT, porque ambos son una
     * clase con el mismo FQN. Los tipos Java son de kvalid-apt.
     */
    @Test
    fun `los tipos JAVA los ignora KSP (son de kvalid-apt)`() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "Kt.kt",
                    """
                    package t
                    import dev.kvalid.annotations.*
                    @Validated
                    data class KotlinDto(@NotBlank val name: String)
                    """.trimIndent(),
                ),
                SourceFile.java(
                    "JavaDto.java",
                    """
                    package t;
                    import dev.kvalid.annotations.*;
                    @Validated
                    public record JavaDto(@NotBlank String reference) {}
                    """.trimIndent(),
                ),
            )
            useKsp2()
            symbolProcessorProviders += KValidProcessorProvider()
            kspProcessorOptions = mutableMapOf("kvalid.componentModel" to "spring")
            inheritClassPath = true
            messageOutputStream = System.out
        }
        val result = compilation.compile()
        check(result.exitCode == KotlinCompilation.ExitCode.OK) { result.messages }

        val names = compilation.generatedFiles().map { it.name }
        assertTrue("KotlinDtoKValidator.kt" in names, "falta el adaptador del tipo Kotlin: $names")
        assertFalse("JavaDtoKValidator.kt" in names, "KSP no debe tocar tipos Java: $names")
        assertFalse("JavaDtoValidator.kt" in names, "tampoco el validador vacío: $names")
    }

    @Test
    fun `un valor no reconocido no rompe la build y no genera adaptador`() {
        val compilation = compilation(source, mapOf("kvalid.componentModel" to "koin"))
        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        assertTrue("kvalid.componentModel" in result.messages, "debe avisar del valor inválido")
        assertFalse(compilation.generatedFiles().any { it.name.contains("KValidator") })
    }
}
