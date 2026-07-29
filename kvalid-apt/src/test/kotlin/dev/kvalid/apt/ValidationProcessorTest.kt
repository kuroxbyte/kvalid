package dev.kvalid.apt

import dev.kvalid.runtime.Violation
import java.io.File
import java.math.BigDecimal
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

    /**
     * Paridad de la ruta Java con la de KSP para los constraints alineados con Jakarta.
     * Importa más que en Kotlin: aquí `boolean`/`int` son primitivos y `String` es nullable,
     * así que las comprobaciones de aplicabilidad recorren otro camino.
     */
    @Test
    fun `record Java - constraints alineados con Jakarta`() {
        val cl = compile(
            mapOf(
                "t/Enrolment.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    import java.time.Instant;
                    @Validated
                    public record Enrolment(
                        @AssertTrue boolean acceptedTerms,
                        @AssertFalse boolean banned,
                        @PositiveOrZero int credits,
                        @NegativeOrZero int balance,
                        @Digits(integer = 4, fraction = 2) String amount,
                        @Null String adminNote,
                        @PastOrPresent Instant createdAt,
                        @FutureOrPresent Instant expiresAt
                    ) {}
                """.trimIndent(),
            ),
        )
        val past = java.time.Instant.now().minusSeconds(3600)
        val future = java.time.Instant.now().plusSeconds(3600)
        fun obj(
            accepted: Boolean = true, banned: Boolean = false, credits: Int = 0, balance: Int = 0,
            amount: String = "1234.56", note: String? = null,
            created: java.time.Instant = past, expires: java.time.Instant = future,
        ) = cl.new("t.Enrolment", accepted, banned, credits, balance, amount, note, created, expires)

        assertTrue(cl.violations("t.Enrolment", obj()).isEmpty(), "el caso válido no debe violar nada")

        assertEquals(listOf("assertTrue"), cl.violations("t.Enrolment", obj(accepted = false)).map { it.code })
        assertEquals(listOf("assertFalse"), cl.violations("t.Enrolment", obj(banned = true)).map { it.code })
        assertEquals(listOf("positiveOrZero"), cl.violations("t.Enrolment", obj(credits = -1)).map { it.code })
        assertEquals(listOf("negativeOrZero"), cl.violations("t.Enrolment", obj(balance = 1)).map { it.code })
        assertEquals(listOf("digits"), cl.violations("t.Enrolment", obj(amount = "12345.6")).map { it.code })
        assertEquals(listOf("null"), cl.violations("t.Enrolment", obj(note = "algo")).map { it.code })
        assertEquals(listOf("pastOrPresent"), cl.violations("t.Enrolment", obj(created = future)).map { it.code })
        assertEquals(listOf("futureOrPresent"), cl.violations("t.Enrolment", obj(expires = past)).map { it.code })
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

    @Test
    fun `NotNull y guarda de nulabilidad en Java`() {
        val cl = compile(
            mapOf(
                "t/Profile.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    @Validated
                    public record Profile(@NotNull String id, @Size(min = 2) String nick) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Profile", cl.new("t.Profile", "u1", "abc")).isEmpty())
        // id null → notNull; nick null NO viola @Size: la guarda de nulabilidad la salta (sin NPE).
        val bad = cl.violations("t.Profile", cl.new("t.Profile", null, null)).associate { it.path to it.code }
        assertEquals("notNull", bad["id"])
        assertTrue("nick" !in bad)
    }

    @Test
    fun `Positive y Negative en Java (int, double, BigDecimal)`() {
        val cl = compile(
            mapOf(
                "t/Nums.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    import java.math.BigDecimal;
                    @Validated
                    public record Nums(@Positive int a, @Negative double b, @Positive BigDecimal c) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Nums", cl.new("t.Nums", 5, -2.0, BigDecimal.TEN)).isEmpty())
        val bad = cl.violations("t.Nums", cl.new("t.Nums", -1, 3.0, BigDecimal.valueOf(-1)))
            .associate { it.path to it.code }
        assertEquals("positive", bad["a"])
        assertEquals("negative", bad["b"])
        assertEquals("positive", bad["c"])
    }

    @Test
    fun `constraints numericas y de texto en Java`() {
        val cl = compile(
            mapOf(
                "t/Order.java" to """
                    package t;
                    import dev.kvalid.annotations.*;
                    import java.math.BigDecimal;
                    @Validated
                    public record Order(
                        @Max(100) int qty,
                        @Range(min = 1, max = 5) long level,
                        @DecimalMin("1.0") @DecimalMax("9.9") BigDecimal price,
                        @Pattern(regex = "[a-z]+") String code,
                        @NotEmpty String note,
                        @OneOf(values = {"A", "B"}) String tier
                    ) {}
                """.trimIndent(),
            ),
        )
        val ok = cl.new("t.Order", 50, 3L, BigDecimal.valueOf(5), "abc", "x", "A")
        assertTrue(cl.violations("t.Order", ok).isEmpty())
        val bad = cl.violations("t.Order", cl.new("t.Order", 200, 9L, BigDecimal.valueOf(20), "AB1", "", "Z"))
            .associate { it.path to it.code }
        assertEquals("max", bad["qty"])
        assertEquals("range", bad["level"])
        assertEquals("decimalMax", bad["price"])
        assertEquals("pattern", bad["code"])
        assertEquals("notEmpty", bad["note"])
        assertEquals("oneOf", bad["tier"])
    }

    @Test
    fun `custom con params en Java cubre literales`() {
        val cl = compile(
            mapOf(
                "t/LenLimitValidator.java" to """
                    package t;
                    import dev.kvalid.runtime.*;
                    import java.util.Map;
                    public final class LenLimitValidator implements ConstraintValidator<String> {
                        @Override
                        public void validate(String value, String field, ValidationContext ctx, Map<String, ?> params) {
                            int max = ((Number) params.get("max")).intValue();
                            if (value.length() > max) ctx.violation(field, "lenLimit", Map.of("max", max), null);
                        }
                    }
                """.trimIndent(),
                "t/LenLimit.java" to """
                    package t;
                    import dev.kvalid.annotations.Constraint;
                    import java.lang.annotation.*;
                    @Constraint(validatedBy = LenLimitValidator.class)
                    @Retention(RetentionPolicy.SOURCE)
                    @Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.TYPE_USE})
                    public @interface LenLimit {
                        int max();
                        long since() default 0L;
                        String label() default "field";
                        boolean strict() default false;
                        double weight() default 1.0;
                    }
                """.trimIndent(),
                "t/Code.java" to """
                    package t;
                    import dev.kvalid.annotations.Validated;
                    @Validated
                    public record Code(@LenLimit(max = 3, since = 1L, label = "slug", strict = true, weight = 2.5) String value) {}
                """.trimIndent(),
            ),
        )
        assertTrue(cl.violations("t.Code", cl.new("t.Code", "abc")).isEmpty())
        assertEquals("lenLimit", cl.violations("t.Code", cl.new("t.Code", "abcd")).single().code)
    }
}
