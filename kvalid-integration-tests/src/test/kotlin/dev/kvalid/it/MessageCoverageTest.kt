package dev.kvalid.it

import dev.kvalid.i18n.DefaultMessageResolver
import dev.kvalid.i18n.DefaultMessages
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * El cierre del círculo: los `code` salen del validador REALMENTE generado por KSP, no de una
 * lista escrita a mano. Si mañana se añade un constraint y se olvida su plantilla, este test
 * cae — mientras que en producción el fallo sería mudo (el resolutor devuelve el `code` y el
 * usuario ve `"notBlank"` en la respuesta).
 */
class MessageCoverageTest {

    /** Un valor que incumple cada constraint, para que salten los 26 a la vez. */
    private val todoMal = Coverage(
        notNullField = null,
        nullField = "algo",
        notBlankField = "   ",
        notEmptyField = emptyList(),
        sizeMinField = "x",
        sizeMaxField = "xxx",
        patternField = "no-son-digitos",
        emailField = "no-es-un-email",
        urlField = "no-es-una-url",
        oneOfField = "C",
        minField = 1,
        maxField = 99,
        rangeField = 1,
        decimalMinField = 0.1,
        decimalMaxField = 9.9,
        digitsField = "1234.56",
        positiveField = -1,
        negativeField = 1,
        positiveOrZeroField = -1,
        negativeOrZeroField = 1,
        assertTrueField = false,
        assertFalseField = true,
        pastField = Instant.now().plusSeconds(3600),
        futureField = Instant.now().minusSeconds(3600),
        pastOrPresentField = Instant.now().plusSeconds(3600),
        futureOrPresentField = Instant.now().minusSeconds(3600),
    )

    private val emitidos: Set<String> = todoMal.validate().violationsOrEmpty().map { it.code }.toSet()

    @Test
    fun `el modelo de cobertura dispara todos los codes conocidos`() {
        // Si esto falla, el modelo se ha quedado corto y el test de abajo no probaría nada.
        assertEquals(DefaultMessages.CODES, emitidos, "el generador y DefaultMessages.CODES divergen")
    }

    @Test
    fun `todo code emitido tiene plantilla en los dos idiomas`() {
        val sinEn = emitidos - DefaultMessages.EN.keys
        val sinEs = emitidos - DefaultMessages.ES.keys
        assertEquals(emptySet(), sinEn, "sin mensaje en inglés")
        assertEquals(emptySet(), sinEs, "sin mensaje en español")
    }

    @Test
    fun `ninguna violacion se resuelve al code crudo`() {
        val es = DefaultMessageResolver(DefaultMessages.ES)
        val crudos = todoMal.validate().violationsOrEmpty()
            .filter { es.resolve(it) == it.code }
            .map { it.code }
        assertEquals(emptyList(), crudos, "estos caen al fallback en vez de dar un mensaje")
    }

    @Test
    fun `los placeholders quedan resueltos, sin llaves sueltas`() {
        val es = DefaultMessageResolver(DefaultMessages.ES)
        todoMal.validate().violationsOrEmpty().forEach { v ->
            val texto = es.resolve(v)
            assertFalse('{' in texto, "quedó un placeholder sin interpolar en '${v.code}': $texto")
            assertTrue(texto.isNotBlank(), "mensaje vacío para '${v.code}'")
        }
    }

    @Test
    fun `los mensajes con parametros llevan el valor real`() {
        val es = DefaultMessageResolver(DefaultMessages.ES)
        val porCode = todoMal.validate().violationsOrEmpty().associateBy { it.code }
        assertEquals("el tamaño debe ser como mínimo 3", es.resolve(porCode.getValue("size.min")))
        assertEquals("debe estar entre 5 y 9", es.resolve(porCode.getValue("range")))
        assertEquals(
            "debe tener como máximo 2 dígitos enteros y 1 decimales",
            es.resolve(porCode.getValue("digits")),
        )
    }
}
