package dev.kvalid.apt

import dev.kvalid.runtime.Violation
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Corre javac con [ValidationProcessor] sobre fuentes Java y ejecuta el `*Validator` generado. */
class ValidationProcessorTest {

    private fun compile(sources: Map<String, String>): ClassLoader {
        val srcDir = Files.createTempDirectory("kvalid-apt-src").toFile()
        val outDir = Files.createTempDirectory("kvalid-apt-out").toFile()
        val files = sources.map { (name, content) -> File(srcDir, name).apply { parentFile.mkdirs(); writeText(content) } }
        val compiler = ToolProvider.getSystemJavaCompiler()
        val diagnostics = javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>()
        val fm = compiler.getStandardFileManager(diagnostics, null, null)
        val units = fm.getJavaFileObjectsFromFiles(files)
        val options = listOf("-d", outDir.absolutePath, "-classpath", System.getProperty("java.class.path"))
        val task = compiler.getTask(null, fm, diagnostics, options, null, units)
        task.setProcessors(listOf(ValidationProcessor()))
        check(task.call()) { "javac + APT falló:\n" + diagnostics.diagnostics.joinToString("\n") }
        return URLClassLoader(arrayOf(outDir.toURI().toURL()), javaClass.classLoader)
    }

    private fun ClassLoader.violations(typeFqn: String, obj: Any): List<Violation> {
        val validator = loadClass("${typeFqn}Validator")
        val result = validator.getMethod("validate", loadClass(typeFqn)).invoke(null, obj)
        @Suppress("UNCHECKED_CAST")
        return result.javaClass.getMethod("violationsOrEmpty").invoke(result) as List<Violation>
    }

    private fun ClassLoader.new(typeFqn: String, vararg args: Any?): Any {
        val c = loadClass(typeFqn).declaredConstructors.first { it.parameterCount == args.size }
        c.isAccessible = true
        return c.newInstance(*args)
    }

    @Test
    fun `record Java - acumula violaciones de constraints`() {
        val cl = compile(
            mapOf(
                "t/User.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    @Validated
                    public record User(@NotBlank @Size(max = 5) String name, @Min(18) int age, @Email String email) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.User", cl.new("t.User", "anna", 30, "anna@x.com")).isEmpty())

        val bad = cl.violations("t.User", cl.new("t.User", "toolong", 10, "nope")).associate { it.path to it.code }
        assertEquals("size.max", bad["name"])
        assertEquals("min", bad["age"])
        assertEquals("email", bad["email"])
    }

    @Test
    fun `cascada anidada Validated en Java`() {
        val cl = compile(
            mapOf(
                "t/Address.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    @Validated
                    public record Address(@NotBlank String street) {}
                """.trimIndent(),
                "t/Order.java" to """
                    package t;
                    import dev.kvalid.annotations.Validated;
                    @Validated
                    public record Order(Address address) {}
                """.trimIndent(),
            ),
        )
        val order = cl.new("t.Order", cl.new("t.Address", " "))
        val v = cl.violations("t.Order", order).single()
        assertEquals("address.street", v.path)
        assertEquals("notBlank", v.code)
    }

    @Test
    fun `constraint custom con validador Java`() {
        val cl = compile(
            mapOf(
                "t/SlugValidator.java" to """
                    package t;
                    import dev.kvalid.runtime.*;
                    import java.util.Map;
                    public final class SlugValidator implements ConstraintValidator<String> {
                        @Override
                        public void validate(String value, String field, ValidationContext ctx, Map<String, ?> params) {
                            if (!value.matches("[a-z0-9-]+")) ctx.violation(field, "slug", Map.of(), null);
                        }
                    }
                """.trimIndent(),
                "t/Slug.java" to """
                    package t;
                    import dev.kvalid.annotations.Constraint;
                    import java.lang.annotation.*;
                    @Constraint(validatedBy = SlugValidator.class)
                    @Retention(RetentionPolicy.SOURCE)
                    @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.TYPE_USE})
                    public @interface Slug {}
                """.trimIndent(),
                "t/Article.java" to """
                    package t;
                    import dev.kvalid.annotations.Validated;
                    @Validated
                    public record Article(@Slug String slug) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Article", cl.new("t.Article", "my-post")).isEmpty())
        val v = cl.violations("t.Article", cl.new("t.Article", "Bad Slug!")).single()
        assertEquals("slug", v.path)
        assertEquals("slug", v.code)
    }

    @Test
    fun `element-level en coleccion Java`() {
        val cl = compile(
            mapOf(
                "t/Post.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    import java.util.List;
                    @Validated
                    public record Post(List<@NotBlank String> tags) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Post", cl.new("t.Post", listOf("a", "b"))).isEmpty())
        val bad = cl.violations("t.Post", cl.new("t.Post", listOf("ok", " ", "  "))).associate { it.path to it.code }
        assertEquals(mapOf("tags[1]" to "notBlank", "tags[2]" to "notBlank"), bad)
    }

    @Test
    fun `validador de clase cross-field en Java`() {
        val cl = compile(
            mapOf(
                "t/PasswordsMatchValidator.java" to """
                    package t;
                    import dev.kvalid.runtime.*;
                    import java.util.Map;
                    public final class PasswordsMatchValidator implements ConstraintValidator<Signup> {
                        @Override
                        public void validate(Signup value, String field, ValidationContext ctx, Map<String, ?> params) {
                            if (!value.password().equals(value.confirm())) ctx.violation("confirm", "passwordsMatch", Map.of(), null);
                        }
                    }
                """.trimIndent(),
                "t/PasswordsMatch.java" to """
                    package t;
                    import dev.kvalid.annotations.Constraint;
                    import java.lang.annotation.*;
                    @Constraint(validatedBy = PasswordsMatchValidator.class)
                    @Retention(RetentionPolicy.SOURCE)
                    @Target(ElementType.TYPE)
                    public @interface PasswordsMatch {}
                """.trimIndent(),
                "t/Signup.java" to """
                    package t;
                    import dev.kvalid.annotations.Validated;
                    @Validated
                    @PasswordsMatch
                    public record Signup(String password, String confirm) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Signup", cl.new("t.Signup", "secret", "secret")).isEmpty())
        val v = cl.violations("t.Signup", cl.new("t.Signup", "secret", "other")).single()
        assertEquals("confirm", v.path)
        assertEquals("passwordsMatch", v.code)
    }

    @Test
    fun `POJO Java con getters`() {
        val cl = compile(
            mapOf(
                "t/Account.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    @Validated
                    public final class Account {
                        private final String handle;
                        public Account(String handle) { this.handle = handle; }
                        @NotBlank public String getHandle() { return handle; }
                    }
                """.trimIndent(),
            ),
        )
        assertEquals("notBlank", cl.violations("t.Account", cl.new("t.Account", "  ")).single().code)
        assertTrue(cl.violations("t.Account", cl.new("t.Account", "ok")).isEmpty())
    }
}
