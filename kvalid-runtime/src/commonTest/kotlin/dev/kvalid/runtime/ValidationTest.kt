package dev.kvalid.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValidationTest {

    @Test
    fun `Valid map transforma y Invalid propaga`() {
        val valid: ValidationResult<Int> = ValidationResult.Valid(21)
        assertEquals(42, valid.map { it * 2 }.getOrNull())
        assertTrue(valid.isValid)

        val invalid: ValidationResult<Int> = ValidationResult.Invalid(listOf(Violation("age", "min")))
        assertNull(invalid.map { it * 2 }.getOrNull())
        assertFalse(invalid.isValid)
        assertEquals(listOf("age"), invalid.violationsOrEmpty().map { it.path })
    }

    @Test
    fun `ValidationContext acumula y rebasa el path`() {
        val ctx = ValidationContext(basePath = "order")
        ctx.violation("total", "min", "min" to 0)
        ctx.violation("date", "after", mapOf("field" to "start"))
        assertEquals(listOf("order.total", "order.date"), ctx.violations.map { it.path })
        assertEquals(mapOf("min" to 0), ctx.violations.first().params)
    }

    @Test
    fun `ValidationContext sin basePath usa el field tal cual`() {
        val ctx = ValidationContext()
        ctx.violation("end", "date.after")
        assertEquals("end", ctx.violations.single().path)
    }

    @Test
    fun `getOrThrow devuelve el valor o lanza con las violaciones`() {
        assertEquals(7, (ValidationResult.Valid(7) as ValidationResult<Int>).getOrThrow())
        val ex = assertFailsWith<ValidationException> {
            (ValidationResult.Invalid(listOf(Violation("age", "min"))) as ValidationResult<Int>).getOrThrow()
        }
        assertEquals(listOf("age"), ex.violations.map { it.path })
    }
}
