@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kvalid.processor

import com.tschuchort.compiletesting.KotlinCompilation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KValidProcessorTest {

    private val user = """
        package t
        import dev.kvalid.annotations.Validated
        import dev.kvalid.annotations.NotBlank
        import dev.kvalid.annotations.Size
        import dev.kvalid.annotations.Range
        import dev.kvalid.annotations.Email
        @Validated
        data class User(
            @NotBlank @Size(max = 5) val name: String,
            @Range(min = 18, max = 120) val age: Int,
            @Email val email: String,
        )
    """.trimIndent()

    @Test
    fun `entrada valida produce Valid`() {
        val r = compileOk(user)
        val u = r.instance("t.User", "anna", 30, "anna@x.com")
        assertTrue(r.validate("t.User", u).isValid)
    }

    @Test
    fun `acumula todas las violaciones con code y path`() {
        val r = compileOk(user)
        val u = r.instance("t.User", "toolongname", 5, "nope")
        val violations = r.validate("t.User", u).violations()
        val byPath = violations.groupBy({ it.path }, { it.code })
        // name: excede max (5); age: fuera de rango; email: inválido
        assertTrue("size.max" in byPath.getValue("name"))
        assertTrue("range" in byPath.getValue("age"))
        assertTrue("email" in byPath.getValue("email"))
        assertEquals(3, violations.size)
    }

    @Test
    fun `NotBlank detecta cadena en blanco`() {
        val r = compileOk(user)
        val u = r.instance("t.User", "   ", 30, "a@x.com")
        val codes = r.validate("t.User", u).violations().map { it.code }
        assertTrue("notBlank" in codes, "$codes")
    }

    @Test
    fun `cascada anidada Validated produce paths con punto`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.NotBlank
            @Validated
            data class Address(@NotBlank val street: String)
            @Validated
            data class Order(val address: Address)
        """.trimIndent()
        val r = compileOk(src)
        val order = r.instance("t.Order", r.instance("t.Address", " "))
        val v = r.validate("t.Order", order).violations().single()
        assertEquals("address.street", v.path)
        assertEquals("notBlank", v.code)
    }

    @Test
    fun `validador de clase (cross-field, ConstraintValidator) se invoca`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Constraint
            import dev.kvalid.runtime.ConstraintValidator
            import dev.kvalid.runtime.ValidationContext

            @Constraint(DateOkValidator::class)
            @Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
            annotation class DateOk

            object DateOkValidator : ConstraintValidator<DateRange> {
                override fun validate(value: DateRange, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
                    if (value.end < value.start) ctx.violation("end", "date.after", "field" to "start")
                }
            }

            @DateOk
            @Validated
            data class DateRange(val start: Int, val end: Int)
        """.trimIndent()
        val r = compileOk(src)
        val v = r.validate("t.DateRange", r.instance("t.DateRange", 10, 5)).violations().single()
        assertEquals("end", v.path)
        assertEquals("date.after", v.code)
        assertEquals("start", v.params["field"])
        assertTrue(r.validate("t.DateRange", r.instance("t.DateRange", 1, 5)).isValid)
    }

    @Test
    fun `constraint custom reutilizable de propiedad (ConstraintValidator) se invoca`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Constraint
            import dev.kvalid.runtime.ConstraintValidator
            import dev.kvalid.runtime.ValidationContext

            @Constraint(SlugValidator::class)
            @Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
            annotation class Slug

            object SlugValidator : ConstraintValidator<String> {
                override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
                    if (value.any { !(it.isLowerCaseLetterOrDigitOrDash()) }) ctx.violation(field, "slug")
                }
                private fun Char.isLowerCaseLetterOrDigitOrDash() = this in 'a'..'z' || this in '0'..'9' || this == '-'
            }

            @Validated
            data class Page(@Slug val handle: String)
        """.trimIndent()
        val r = compileOk(src)
        val bad = r.validate("t.Page", r.instance("t.Page", "Bad Handle")).violations().single()
        assertEquals("handle", bad.path)
        assertEquals("slug", bad.code)
        assertTrue(r.validate("t.Page", r.instance("t.Page", "ok-slug-1")).isValid)
    }

    @Test
    fun `constraint custom parametrizado recibe los args de la anotacion`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Constraint
            import dev.kvalid.runtime.ConstraintValidator
            import dev.kvalid.runtime.ValidationContext

            @Constraint(LengthValidator::class)
            @Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
            annotation class Length(val max: Int)

            object LengthValidator : ConstraintValidator<String> {
                override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
                    val max = params["max"] as Int
                    if (value.length > max) ctx.violation(field, "length", "max" to max)
                }
            }

            @Validated
            data class Tag(@Length(max = 3) val code: String)
        """.trimIndent()
        val r = compileOk(src)
        val v = r.validate("t.Tag", r.instance("t.Tag", "abcd")).violations().single()
        assertEquals("length", v.code)
        assertEquals(3, v.params["max"])
        assertTrue(r.validate("t.Tag", r.instance("t.Tag", "abc")).isValid)
    }

    @Test
    fun `precision Long - Min compara sin perder precision (no toDouble)`() {
        // 2^53 y 2^53+1 colapsan al mismo Double: con toDouble el chequeo daría un falso OK.
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Min
            @Validated
            data class Big(@Min(9007199254740993L) val n: Long)
        """.trimIndent()
        val r = compileOk(src)
        // n = 2^53 (< min 2^53+1) → DEBE ser inválido; con toDouble sería (erróneamente) válido.
        val bad = r.validate("t.Big", r.instance("t.Big", 9007199254740992L)).violations()
        assertEquals(listOf("min"), bad.map { it.code })
        assertTrue(r.validate("t.Big", r.instance("t.Big", 9007199254740993L)).isValid)
    }

    @Test
    fun `BigDecimal - DecimalMin compara exacto`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            import java.math.BigDecimal
            @Validated
            data class Price(@DecimalMin("0.01") val amount: BigDecimal)
        """.trimIndent()
        val r = compileOk(src)
        val bad = r.validate("t.Price", r.instance("t.Price", java.math.BigDecimal("0.005"))).violations()
        assertEquals(listOf("decimalMin"), bad.map { it.code })
        assertTrue(r.validate("t.Price", r.instance("t.Price", java.math.BigDecimal("0.02"))).isValid)
        // 1.0 vs 1.00 (mismo valor, distinto scale) no debe importar en la comparación:
        assertTrue(r.validate("t.Price", r.instance("t.Price", java.math.BigDecimal("1.00"))).isValid)
    }

    @Test
    fun `message opcional llega a la Violation`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.NotBlank
            @Validated
            data class U(@NotBlank(message = "obligatorio") val name: String)
        """.trimIndent()
        val r = compileOk(src)
        val v = r.validate("t.U", r.instance("t.U", "  ")).violations().single()
        assertEquals("notBlank", v.code)
        assertEquals("obligatorio", v.message)
    }

    @Test
    fun `anotacion compuesta expande sus constraints`() {
        val src = """
            package t
            import dev.kvalid.annotations.*
            @NotBlank @Size(min = 3, max = 8)
            @Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
            annotation class Username
            @Validated
            data class Account(@Username val handle: String)
        """.trimIndent()
        val r = compileOk(src)
        assertEquals("size.min", r.validate("t.Account", r.instance("t.Account", "ab")).violations().single().code)
        assertEquals("size.max", r.validate("t.Account", r.instance("t.Account", "demasiadolargo")).violations().single().code)
        assertTrue(r.validate("t.Account", r.instance("t.Account", "handle")).isValid)  // 6 chars, ok
    }

    @Test
    fun `OneOf valida contra el conjunto`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.OneOf
            @Validated
            data class Cfg(@OneOf("dev", "prod") val env: String)
        """.trimIndent()
        val r = compileOk(src)
        assertEquals(listOf("oneOf"), r.validate("t.Cfg", r.instance("t.Cfg", "staging")).violations().map { it.code })
        assertTrue(r.validate("t.Cfg", r.instance("t.Cfg", "prod")).isValid)
    }

    @Test
    fun `constraints a nivel de elemento (List de String) se validan por indice`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.NotBlank
            import dev.kvalid.annotations.Size
            @Validated
            data class Post(val tags: List<@NotBlank @Size(max = 5) String>)
        """.trimIndent()
        val r = compileOk(src)
        val changes = r.validate("t.Post", r.instance("t.Post", listOf("ok", "  ", "demasiado")))
            .violations().associate { it.path to it.code }
        // índice 1 en blanco → notBlank; índice 2 excede 5 → size.max
        assertEquals("notBlank", changes["tags[1]"])
        assertEquals("size.max", changes["tags[2]"])
        assertTrue(r.validate("t.Post", r.instance("t.Post", listOf("a", "bb"))).isValid)
    }

    @Test
    fun `cascada en elementos de coleccion Validated`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.NotBlank
            @Validated
            data class Line(@NotBlank val sku: String)
            @Validated
            data class Order(val lines: List<Line>)
        """.trimIndent()
        val r = compileOk(src)
        fun line(sku: String) = r.instance("t.Line", sku)
        val order = r.instance("t.Order", listOf(line("A"), line("  "), line("C")))
        val v = r.validate("t.Order", order).violations().single()
        assertEquals("lines[1].sku", v.path)
        assertEquals("notBlank", v.code)
        assertTrue(r.validate("t.Order", r.instance("t.Order", listOf(line("A"), line("B")))).isValid)
    }

    @Test
    fun `Past y Future validan Instant respecto a ahora`() {
        val src = """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Past
            import dev.kvalid.annotations.Future
            import java.time.Instant
            @Validated
            data class Event(@Past val createdAt: Instant, @Future val expiresAt: Instant)
        """.trimIndent()
        val r = compileOk(src)
        val past = java.time.Instant.now().minusSeconds(3600)
        val future = java.time.Instant.now().plusSeconds(3600)
        assertTrue(r.validate("t.Event", r.instance("t.Event", past, future)).isValid)
        // createdAt en el futuro y expiresAt en el pasado → dos violaciones
        val codes = r.validate("t.Event", r.instance("t.Event", future, past)).violations().map { it.code }.toSet()
        assertEquals(setOf("past", "future"), codes)
    }

    @Test
    fun `bounds invalidos son error de compilacion`() {
        val result = compile(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Size
            @Validated
            data class Bad(@Size(min = 5, max = 1) val name: String)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("kvalid.constraint.args" in result.messages, result.messages)
    }

    @Test
    fun `constraint mal aplicada es error de compilacion`() {
        val result = compile(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Range
            @Validated
            data class Bad(@Range(min = 0, max = 9) val name: String)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("kvalid.constraint.type" in result.messages, result.messages)
    }

    @Test
    fun `Digits sobre Double es error y explica por que`() {
        val result = compile(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Digits
            @Validated
            data class Bad(@Digits(integer = 3, fraction = 2) val price: Double)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("kvalid.constraint.type" in result.messages, result.messages)
        // El mensaje genérico ("se esperaba un tipo numérico") despistaría: Double SÍ es numérico.
        assertTrue("coma flotante" in result.messages, result.messages)
    }

    @Test
    fun `Digits sobre BigDecimal usa la forma plana, no la cientifica`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Digits
            import java.math.BigDecimal
            @Validated
            data class Money(@Digits(integer = 3, fraction = 0) val amount: BigDecimal)
            """.trimIndent(),
        )
        // BigDecimal("1E+2").toString() == "1E+2" (no contable → violaría), pero
        // toPlainString() == "100", que son 3 dígitos enteros y SÍ cabe. El test distingue
        // las dos rutas: si el emisor usara toString(), esto fallaría.
        val scientific = java.math.BigDecimal("1E+2")
        assertEquals("1E+2", scientific.toString(), "premisa del test")
        assertTrue(r.validate("t.Money", r.instance("t.Money", scientific)).isValid)

        val tooLong = java.math.BigDecimal("1E+4") // 10000 → 5 dígitos enteros
        assertEquals(listOf("digits"), r.validate("t.Money", r.instance("t.Money", tooLong)).violations().map { it.code })
    }

    @Test
    fun `Null sobre una propiedad no-nullable es error de compilacion`() {
        val result = compile(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.Null
            @Validated
            data class Bad(@Null val note: String)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("kvalid.constraint.type" in result.messages, result.messages)
    }

    @Test
    fun `AssertTrue sobre un no-Boolean es error de compilacion`() {
        val result = compile(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.AssertTrue
            @Validated
            data class Bad(@AssertTrue val name: String)
            """.trimIndent(),
        )
        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue("kvalid.constraint.type" in result.messages, result.messages)
    }
}
