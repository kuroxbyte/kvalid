package dev.kvalid.runtime

/**
 * Cuenta de dígitos para `@Digits`, en `commonMain`.
 *
 * Vive aquí, y no inline en el código generado, por dos razones: `java.math.BigDecimal` no
 * existe fuera de la JVM (emitirlo rompería iOS/JS/Wasm), y la cuenta tiene suficientes casos
 * de borde —signo, ceros a la izquierda, cadenas no numéricas— como para querer testearla una
 * vez en lugar de una por cada validador generado.
 */
public object Digits {

    /**
     * `true` si [value] **incumple** el límite: más de [integer] dígitos enteros
     * significativos o más de [fraction] decimales.
     *
     * Se cuentan dígitos *significativos* en la parte entera, así que `0.5` tiene 0 dígitos
     * enteros y `007` tiene 1. Los ceros a la derecha de los decimales **sí** cuentan (`1.50`
     * son 2 decimales), igual que en Jakarta, porque provienen de la escala declarada.
     *
     * Una cadena que no sea un decimal simple —vacía, con letras o en notación científica—
     * se considera incumplimiento: no hay forma de contarle los dígitos.
     */
    public fun exceeds(value: String, integer: Int, fraction: Int): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true

        val body = if (trimmed[0] == '+' || trimmed[0] == '-') trimmed.substring(1) else trimmed
        val dot = body.indexOf('.')
        val intPart = if (dot < 0) body else body.substring(0, dot)
        val fracPart = if (dot < 0) "" else body.substring(dot + 1)

        if (intPart.isEmpty() && fracPart.isEmpty()) return true
        // Un segundo punto o cualquier no-dígito (incluida la 'E' de la notación científica)
        // cae aquí: no es un decimal que se pueda contar.
        if (!intPart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return true

        return intPart.trimStart('0').length > integer || fracPart.length > fraction
    }
}
