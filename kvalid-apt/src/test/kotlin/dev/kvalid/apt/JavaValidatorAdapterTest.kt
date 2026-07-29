package dev.kvalid.apt

import dev.kvalid.runtime.ValidationResult
import dev.kvalid.runtime.spi.KValidator
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Espejo de `ValidatorAdapterTest` (KSP) para el frontend **Java/APT**: mismo contrato de la
 * opción `kvalid.componentModel`, aquí pasada a javac como `-Akvalid.componentModel=...`.
 */
class JavaValidatorAdapterTest {

    private class Compiled(val classes: File, val sources: File, val messages: String) {
        val classLoader: ClassLoader get() = URLClassLoader(arrayOf(classes.toURI().toURL()), Compiled::class.java.classLoader)
        fun generatedSources(): List<File> = sources.walkTopDown().filter { it.isFile }.toList()
        fun generated(name: String): File? = generatedSources().firstOrNull { it.name == name }
    }

    private fun compile(
        source: String,
        options: Map<String, String> = emptyMap(),
        fileName: String = "t/Account.java",   // javac exige que coincida con la clase pública
    ): Compiled {
        val srcDir = Files.createTempDirectory("kvalid-apt-adapter-src").toFile()
        val outDir = Files.createTempDirectory("kvalid-apt-adapter-out").toFile()
        val genDir = Files.createTempDirectory("kvalid-apt-adapter-gen").toFile()
        val file = File(srcDir, fileName).apply { parentFile.mkdirs(); writeText(source) }

        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>()
        val fm = compiler.getStandardFileManager(diagnostics, null, null)
        val args = buildList {
            add("-d"); add(outDir.absolutePath)
            add("-s"); add(genDir.absolutePath)          // dónde deja los fuentes generados
            add("-classpath"); add(System.getProperty("java.class.path"))
            options.forEach { (k, v) -> add("-A$k=$v") }
        }
        val task = compiler.getTask(null, fm, diagnostics, args, null, fm.getJavaFileObjectsFromFiles(listOf(file)))
        task.setProcessors(listOf(ValidationProcessor()))
        val ok = task.call()
        val messages = diagnostics.diagnostics.joinToString("\n") { it.getMessage(null) }
        check(ok) { "javac + APT falló:\n$messages" }
        return Compiled(outDir, genDir, messages)
    }

    private val source = """
        package t;
        import dev.kvalid.annotations.*;

        @Validated
        public record Account(@NotBlank String name, @Min(18) int age) {}
    """.trimIndent()

    @Test
    fun `por defecto NO se genera adaptador`() {
        val result = compile(source)
        val names = result.generatedSources().map { it.name }
        assertTrue(names.any { it == "AccountValidator.java" }, "falta el validador de siempre: $names")
        assertFalse(names.any { it.contains("KValidator") }, "no debería haber adaptador: $names")
    }

    @Test
    fun `componentModel=spring genera adaptador Java con @Component`() {
        val result = compile(source, mapOf("kvalid.componentModel" to "spring"))

        val adapter = result.generated("AccountKValidator.java")
        assertNotNull(adapter, "no se generó el adaptador: ${result.generatedSources().map { it.name }}")

        val code = adapter.readText()
        assertTrue("@Component" in code, code)
        assertTrue("implements KValidator<Account>" in code, code)
        assertTrue("AccountValidator.validate(value)" in code, "debe DELEGAR, no reimplementar:\n$code")
    }

    @Test
    fun `el adaptador Java funciona en runtime y delega en el validador generado`() {
        val result = compile(source, mapOf("kvalid.componentModel" to "spring"))
        val cl = result.classLoader

        @Suppress("UNCHECKED_CAST")
        val adapter = cl.loadClass("t.AccountKValidator").getDeclaredConstructor().newInstance()
            as KValidator<Any>
        val accountClass = cl.loadClass("t.Account")
        assertEquals(accountClass, adapter.type)

        val ctor = accountClass.getDeclaredConstructor(String::class.java, Int::class.java)
        assertTrue(adapter.validate(ctor.newInstance("Ana", 30)) is ValidationResult.Valid)

        val violations = adapter.validate(ctor.newInstance("", 15)).violationsOrEmpty()
        assertEquals(setOf("notBlank", "min"), violations.map { it.code }.toSet())
    }

    @Test
    fun `componentModel=serviceloader genera el META-INF-services sin @Component`() {
        val result = compile(source, mapOf("kvalid.componentModel" to "serviceloader"))

        val adapter = result.generated("AccountKValidator.java")
        assertNotNull(adapter)
        assertFalse("@Component" in adapter.readText(), "sin Spring no debe llevar @Component")

        val services = File(result.classes, "META-INF/services/dev.kvalid.runtime.spi.KValidator")
        assertTrue(services.isFile, "falta ${services.path}")
        assertEquals("t.AccountKValidator", services.readText().trim())
    }

    @Test
    fun `un valor no reconocido avisa y no genera adaptador`() {
        val result = compile(source, mapOf("kvalid.componentModel" to "koin"))

        assertTrue("kvalid.componentModel" in result.messages, "debe avisar: ${result.messages}")
        assertFalse(result.generatedSources().any { it.name.contains("KValidator") })
    }

    /**
     * Regresión (genkit 0.1.1): el paquete de un tipo Java **anidado** se derivaba del nombre
     * calificado, así que `t.Outer.Inner` daba paquete `t.Outer` — la clase externa. El
     * generado caía en un paquete inexistente y javac fallaba con *"class Outer clashes with
     * package of same name"*: **ningún tipo Java anidado era usable**.
     */
    @Test
    fun `un tipo Java ANIDADO genera en el paquete correcto`() {
        val nested = """
            package t;
            import dev.kvalid.annotations.*;

            public final class Outer {
                @Validated
                public record Inner(@NotBlank String code) {}
            }
        """.trimIndent()

        // Que compile YA es la aserción principal: antes reventaba en javac.
        val result = compile(nested, mapOf("kvalid.componentModel" to "spring"), fileName = "t/Outer.java")

        // El adaptador aplana el nombre anidado (Outer+Inner) para no colisionar con otro
        // `Inner` de distinto contenedor en el mismo paquete.
        val adapter = result.generated("OuterInnerKValidator.java")
        assertNotNull(adapter, "no se generó: ${result.generatedSources().map { it.name }}")
        val code = adapter.readText()
        assertTrue("package t;" in code, "debe generar en el paquete 't', no en 't.Outer':\n$code")
        assertTrue("Outer.Inner" in code, "debe referenciar el tipo anidado como Outer.Inner:\n$code")

        // El validador de siempre también cae en el paquete correcto.
        assertTrue("package t;" in result.generated("InnerValidator.java")!!.readText())
    }
}
