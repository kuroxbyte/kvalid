package dev.kvalid.it

import dev.kvalid.runtime.ValidationResult
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los constraints alineados con Jakarta, ejercitados sobre el código REALMENTE generado por
 * KSP (no sobre el modelo del emisor): si el `validate()` emitido no compilara o comparara al
 * revés, estos tests caen.
 */
class JakartaParityTest {

    private val past = Instant.now().minusSeconds(3600)
    private val future = Instant.now().plusSeconds(3600)

    /** Todo correcto: ni una sola violación. Es el control de los demás tests. */
    private fun valid() = Enrolment(
        acceptedTerms = true,
        banned = false,
        credits = 0,
        balance = 0,
        amount = "1234.56",
        adminNote = null,
        createdAt = past,
        expiresAt = future,
    )

    private fun codesOf(r: ValidationResult<*>) = r.violationsOrEmpty().map { it.code }

    @Test
    fun `un objeto válido no produce violaciones`() {
        assertTrue(valid().validate().isValid, "violaciones: ${codesOf(valid().validate())}")
    }

    @Test
    fun `AssertTrue y AssertFalse comparan en el sentido correcto`() {
        // El cero de credits/balance ya prueba que OrZero acepta el límite.
        assertEquals(listOf("assertTrue"), codesOf(valid().copy(acceptedTerms = false).validate()))
        assertEquals(listOf("assertFalse"), codesOf(valid().copy(banned = true).validate()))
    }

    @Test
    fun `PositiveOrZero y NegativeOrZero solo rechazan el signo contrario`() {
        assertEquals(listOf("positiveOrZero"), codesOf(valid().copy(credits = -1).validate()))
        assertEquals(listOf("negativeOrZero"), codesOf(valid().copy(balance = 1).validate()))
        // El límite (0) es válido: ya cubierto por `valid()`, y el signo propio también.
        assertTrue(valid().copy(credits = 5, balance = -5).validate().isValid)
    }

    @Test
    fun `Digits cuenta enteros y decimales por separado`() {
        assertTrue(valid().copy(amount = "0.5").validate().isValid, "0.5 cabe en (4,2)")
        assertEquals(listOf("digits"), codesOf(valid().copy(amount = "12345.6").validate()))
        assertEquals(listOf("digits"), codesOf(valid().copy(amount = "1.234").validate()))
        assertEquals(listOf("digits"), codesOf(valid().copy(amount = "no-es-un-numero").validate()))
    }

    @Test
    fun `Digits expone los límites como params para el mensaje`() {
        val v = valid().copy(amount = "1.234").validate().violationsOrEmpty().single()
        assertEquals(mapOf("integer" to 4, "fraction" to 2), v.params)
    }

    @Test
    fun `Null exige ausencia`() {
        assertEquals(listOf("null"), codesOf(valid().copy(adminNote = "algo").validate()))
    }

    @Test
    fun `PastOrPresent y FutureOrPresent aceptan el propio instante`() {
        assertEquals(listOf("pastOrPresent"), codesOf(valid().copy(createdAt = future).validate()))
        assertEquals(listOf("futureOrPresent"), codesOf(valid().copy(expiresAt = past).validate()))
    }

    @Test
    fun `la ruta de la violación es el nombre del campo`() {
        val v = valid().copy(acceptedTerms = false).validate().violationsOrEmpty().single()
        assertEquals("acceptedTerms", v.path)
    }
}
