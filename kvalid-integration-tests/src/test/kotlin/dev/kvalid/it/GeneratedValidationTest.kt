package dev.kvalid.it

import dev.kvalid.i18n.DefaultMessageResolver
import dev.kvalid.runtime.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Consume la extensión `validate()` generada por KSP **directamente** (sin reflexión). Cubre
 * constraints built-in, custom (`@Slug`), element-level (`List<@NotBlank String>`), cross-field
 * (`@PasswordsMatch`) e i18n del resultado.
 */
class GeneratedValidationTest {

    private fun codes(r: ValidationResult<*>) = r.violationsOrEmpty().associate { it.path to it.code }

    @Test
    fun `constraints built-in acumulan todas las violaciones`() {
        assertTrue(User("Ana", 30, "ana@x.com").validate() is ValidationResult.Valid)
        val bad = codes(User("", 15, "nope").validate())
        assertEquals(mapOf("name" to "notBlank", "age" to "min", "email" to "email"), bad)
    }

    @Test
    fun `custom Slug y element-level en la misma clase`() {
        assertTrue(Article("my-post", listOf("a", "b")).validate() is ValidationResult.Valid)
        val bad = codes(Article("Bad Slug!", listOf("ok", " ")).validate())
        assertEquals("slug", bad["slug"])
        assertEquals("notBlank", bad["tags[1]"])
    }

    @Test
    fun `validador de clase cross-field`() {
        assertTrue(Signup("secret", "secret").validate() is ValidationResult.Valid)
        assertEquals("passwordsMatch", codes(Signup("secret", "other").validate())["confirm"])
    }

    @Test
    fun `i18n resuelve code a mensaje`() {
        val es = DefaultMessageResolver(mapOf("min" to "Debe ser al menos {min}"))
        val v = User("Ana", 15, "ana@x.com").validate().violationsOrEmpty().single()
        assertEquals("Debe ser al menos 18", es.resolve(v))
    }
}
