package dev.kvalid.core.build

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * La cota de `@DecimalMin`/`@DecimalMax` redondeada para comparar contra una propiedad ENTERA.
 *
 * La cota es una constante conocida al generar, así que el redondeo se hace aquí y no en cada
 * validación: para un `v` entero, `v < 0.5` es exactamente `v < 1`. Eso permite emitir una
 * comparación de `Long` —sin `BigDecimal`, que no existe fuera de la JVM, y sin asignar nada—
 * conservando el resultado exacto.
 *
 * Se redondea hacia arriba para el mínimo (`v < cota` ⟺ `v < ⌈cota⌉`) y hacia abajo para el
 * máximo (`v > cota` ⟺ `v > ⌊cota⌋`). Funciona igual con cotas negativas: `v < -0.5` ⟺ `v < 0`.
 */
public sealed interface IntegerBound {

    /** La cota redondeada cabe en un `Long` y se puede emitir como literal. */
    public data class Fits(val value: Long) : IntegerBound

    /** La cota queda por encima de cualquier `Long`: la comparación es constante. */
    public data object AboveAll : IntegerBound

    /** La cota queda por debajo de cualquier `Long`: la comparación es constante. */
    public data object BelowAll : IntegerBound

    public companion object {
        private val MAX = BigInteger.valueOf(Long.MAX_VALUE)
        private val MIN = BigInteger.valueOf(Long.MIN_VALUE)

        /** Para `@DecimalMin`: `v < cota` ⟺ `v < ⌈cota⌉`. */
        public fun forMin(value: String): IntegerBound = round(value, RoundingMode.CEILING)

        /** Para `@DecimalMax`: `v > cota` ⟺ `v > ⌊cota⌋`. */
        public fun forMax(value: String): IntegerBound = round(value, RoundingMode.FLOOR)

        private fun round(value: String, mode: RoundingMode): IntegerBound {
            val rounded = BigDecimal(value).setScale(0, mode).toBigIntegerExact()
            return when {
                rounded > MAX -> AboveAll
                rounded < MIN -> BelowAll
                else -> Fits(rounded.toLong())
            }
        }
    }
}
