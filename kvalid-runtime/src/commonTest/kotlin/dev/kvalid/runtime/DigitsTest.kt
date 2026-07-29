package dev.kvalid.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Casos de borde de la cuenta de dígitos: es donde `@Digits` se equivoca en silencio. */
class DigitsTest {

    @Test
    fun `cuenta enteros y decimales por separado`() {
        assertFalse(Digits.exceeds("1234.56", integer = 4, fraction = 2))
        assertTrue(Digits.exceeds("12345.6", integer = 4, fraction = 2))
        assertTrue(Digits.exceeds("1234.567", integer = 4, fraction = 2))
    }

    @Test
    fun `el signo no cuenta como digito`() {
        assertFalse(Digits.exceeds("-1234.56", integer = 4, fraction = 2))
        assertFalse(Digits.exceeds("+1234.56", integer = 4, fraction = 2))
    }

    @Test
    fun `los ceros a la izquierda no son significativos`() {
        assertFalse(Digits.exceeds("007", integer = 1, fraction = 0))
        // 0.5 no tiene dígitos enteros significativos: cabe incluso en integer = 0.
        assertFalse(Digits.exceeds("0.5", integer = 0, fraction = 1))
    }

    @Test
    fun `los ceros a la derecha del decimal SI cuentan`() {
        // Vienen de la escala declarada (1.50 son dos decimales), igual que en Jakarta.
        assertTrue(Digits.exceeds("1.50", integer = 1, fraction = 1))
        assertFalse(Digits.exceeds("1.50", integer = 1, fraction = 2))
    }

    @Test
    fun `sin parte decimal la fraccion es cero`() {
        assertFalse(Digits.exceeds("123", integer = 3, fraction = 0))
        assertTrue(Digits.exceeds("1234", integer = 3, fraction = 0))
    }

    @Test
    fun `lo que no es un decimal simple se considera incumplimiento`() {
        assertTrue(Digits.exceeds("", integer = 9, fraction = 9))
        assertTrue(Digits.exceeds("   ", integer = 9, fraction = 9))
        assertTrue(Digits.exceeds("abc", integer = 9, fraction = 9))
        assertTrue(Digits.exceeds("1.2.3", integer = 9, fraction = 9))
        assertTrue(Digits.exceeds("1E+2", integer = 9, fraction = 9), "la notación científica no es contable")
        assertTrue(Digits.exceeds("-", integer = 9, fraction = 9))
    }

    @Test
    fun `se ignoran los espacios alrededor`() {
        assertFalse(Digits.exceeds("  12.3  ", integer = 2, fraction = 1))
    }
}
