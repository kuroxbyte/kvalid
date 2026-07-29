@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kvalid.processor

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `@DecimalMin`/`@DecimalMax` emitían `java.math.BigDecimal` para CUALQUIER tipo numérico. Eso
 * ataba el generado a la JVM (en un `commonMain` con targets nativos ni siquiera compilaba) y
 * construía dos objetos en cada validación.
 *
 * Ahora la comparación depende del tipo. Estos tests fijan las dos cosas: que el resultado
 * sigue siendo exacto, y que el código emitido tiene la forma correcta.
 */
class DecimalBoundsTest {

    // ── Portabilidad: la regresión que motivó el cambio ──────────────────────────

    @Test
    fun `sobre tipos multiplataforma no se emite java punto math`() {
        val text = compilation(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            import dev.kvalid.annotations.DecimalMax
            @Validated
            data class M(
                @DecimalMin("0.01") val price: Double,
                @DecimalMax("99.9") val ratio: Float,
                @DecimalMin("0.5") val count: Int,
                @DecimalMax("10.5") val total: Long,
            )
            """.trimIndent(),
        ).generatedText()

        assertFalse(
            "java.math" in text,
            "el generado debe compilar en Native/JS, y ahí no existe java.math:\n$text",
        )
    }

    @Test
    fun `sobre BigDecimal si se usa BigDecimal, pero la cota se iza a constante`() {
        val text = compilation(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            import java.math.BigDecimal
            @Validated
            data class M(@DecimalMin("0.01") val a: BigDecimal, @DecimalMin("0.01") val b: BigDecimal)
            """.trimIndent(),
        ).generatedText()

        // BigDecimal es legítimo aquí: una propiedad BigDecimal ya es solo-JVM de por sí.
        assertTrue("private val DEC_0_01: BigDecimal = BigDecimal(\"0.01\")" in text, text)
        // Una sola constante para las dos propiedades: la cota deriva del valor, no del campo.
        assertEquals(1, Regex("private val DEC_0_01").findAll(text).count(), text)
        // Y ya no se construye nada dentro de la condición.
        assertFalse("compareTo(BigDecimal(" in text, text)
    }

    // ── Exactitud: el redondeo de la cota no puede cambiar el resultado ──────────

    @Test
    fun `cota fraccionaria sobre entero - minimo`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            @Validated
            data class M(@DecimalMin("0.5") val n: Int)
            """.trimIndent(),
        )
        // n < 0.5 ⟺ n < 1 para n entero.
        assertEquals(listOf("decimalMin"), r.validate("t.M", r.instance("t.M", 0)).violations().map { it.code })
        assertTrue(r.validate("t.M", r.instance("t.M", 1)).isValid)
    }

    @Test
    fun `cota fraccionaria sobre entero - maximo`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMax
            @Validated
            data class M(@DecimalMax("0.5") val n: Int)
            """.trimIndent(),
        )
        // n > 0.5 ⟺ n > 0 para n entero.
        assertTrue(r.validate("t.M", r.instance("t.M", 0)).isValid)
        assertEquals(listOf("decimalMax"), r.validate("t.M", r.instance("t.M", 1)).violations().map { it.code })
    }

    @Test
    fun `cota negativa fraccionaria sobre entero`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            @Validated
            data class M(@DecimalMin("-0.5") val n: Int)
            """.trimIndent(),
        )
        // n < -0.5 ⟺ n <= -1. El redondeo hacia arriba de -0.5 es 0, así que la condición es n < 0.
        assertEquals(listOf("decimalMin"), r.validate("t.M", r.instance("t.M", -1)).violations().map { it.code })
        assertTrue(r.validate("t.M", r.instance("t.M", 0)).isValid)
    }

    @Test
    fun `sobre Double compara en el propio tipo`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            @Validated
            data class M(@DecimalMin("0.01") val price: Double)
            """.trimIndent(),
        )
        assertEquals(listOf("decimalMin"), r.validate("t.M", r.instance("t.M", 0.005)).violations().map { it.code })
        assertTrue(r.validate("t.M", r.instance("t.M", 0.01)).isValid, "la cota es inclusiva")
        assertTrue(r.validate("t.M", r.instance("t.M", 2.0)).isValid)
    }

    @Test
    fun `sobre BigDecimal sigue siendo exacto e ignora la escala`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            import java.math.BigDecimal
            @Validated
            data class M(@DecimalMin("1.0") val a: BigDecimal)
            """.trimIndent(),
        )
        // 1.00 == 1.0 comparando con compareTo (no con equals, que sí mira la escala).
        assertTrue(r.validate("t.M", r.instance("t.M", BigDecimal("1.00"))).isValid)
        assertEquals(
            listOf("decimalMin"),
            r.validate("t.M", r.instance("t.M", BigDecimal("0.999"))).violations().map { it.code },
        )
    }

    @Test
    fun `una cota que no cabe en Long no genera un literal truncado`() {
        val r = compileOk(
            """
            package t
            import dev.kvalid.annotations.Validated
            import dev.kvalid.annotations.DecimalMin
            @Validated
            data class M(@DecimalMin("1E30") val n: Int)
            """.trimIndent(),
        )
        // Ningún Int llega a 1E30, así que TODO valor la incumple.
        assertEquals(listOf("decimalMin"), r.validate("t.M", r.instance("t.M", Int.MAX_VALUE)).violations().map { it.code })
    }
}
