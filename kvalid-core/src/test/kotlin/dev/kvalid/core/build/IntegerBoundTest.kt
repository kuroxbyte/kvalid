package dev.kvalid.core.build

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El redondeo de la cota se hace UNA vez, al generar. Si se equivoca, el validador emitido
 * acepta o rechaza de más para siempre y sin ruido, así que conviene fijarlo aquí.
 *
 * La equivalencia que se comprueba: para `v` entero, `v < cota` ⟺ `v < ⌈cota⌉`
 * y `v > cota` ⟺ `v > ⌊cota⌋`.
 */
class IntegerBoundTest {

    private fun min(v: String) = IntegerBound.forMin(v)
    private fun max(v: String) = IntegerBound.forMax(v)

    @Test
    fun `una cota entera se queda como está`() {
        assertEquals(IntegerBound.Fits(5), min("5"))
        assertEquals(IntegerBound.Fits(5), max("5"))
        assertEquals(IntegerBound.Fits(5), min("5.00"))
    }

    @Test
    fun `el minimo redondea hacia arriba`() {
        // v < 0.5 ⟺ v < 1 para v entero.
        assertEquals(IntegerBound.Fits(1), min("0.5"))
        assertEquals(IntegerBound.Fits(1), min("0.01"))
        assertEquals(IntegerBound.Fits(3), min("2.7"))
    }

    @Test
    fun `el maximo redondea hacia abajo`() {
        // v > 0.5 ⟺ v > 0 para v entero.
        assertEquals(IntegerBound.Fits(0), max("0.5"))
        assertEquals(IntegerBound.Fits(2), max("2.7"))
    }

    @Test
    fun `las cotas negativas redondean en el sentido correcto`() {
        // v < -0.5 ⟺ v <= -1 ⟺ v < 0
        assertEquals(IntegerBound.Fits(0), min("-0.5"))
        // v > -0.5 ⟺ v >= 0 ⟺ v > -1
        assertEquals(IntegerBound.Fits(-1), max("-0.5"))
    }

    @Test
    fun `una cota fuera del rango de Long no se trunca`() {
        // Truncarla daría un literal absurdo; se marca para emitir una comparación constante.
        assertEquals(IntegerBound.AboveAll, min("1E30"))
        assertEquals(IntegerBound.AboveAll, max("1E30"))
        assertEquals(IntegerBound.BelowAll, min("-1E30"))
        assertEquals(IntegerBound.BelowAll, max("-1E30"))
    }

    @Test
    fun `los limites exactos de Long si caben`() {
        assertEquals(IntegerBound.Fits(Long.MAX_VALUE), min(Long.MAX_VALUE.toString()))
        assertEquals(IntegerBound.Fits(Long.MIN_VALUE), max(Long.MIN_VALUE.toString()))
    }
}
