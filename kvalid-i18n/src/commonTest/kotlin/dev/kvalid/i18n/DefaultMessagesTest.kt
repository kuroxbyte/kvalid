package dev.kvalid.i18n

import dev.kvalid.runtime.Violation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Un mensaje que falta no rompe nada: el resolutor cae al `code` y el usuario ve `"notBlank"`.
 * Como el fallo es silencioso, la cobertura se comprueba aquí.
 */
class DefaultMessagesTest {

    @Test
    fun `los dos idiomas cubren todos los codes`() {
        assertEquals(emptySet(), DefaultMessages.CODES - DefaultMessages.EN.keys, "faltan en EN")
        assertEquals(emptySet(), DefaultMessages.CODES - DefaultMessages.ES.keys, "faltan en ES")
    }

    @Test
    fun `ningun idioma tiene claves de mas`() {
        // Una clave que no emite el generador es texto muerto, o una errata.
        assertEquals(emptySet(), DefaultMessages.EN.keys - DefaultMessages.CODES)
        assertEquals(emptySet(), DefaultMessages.ES.keys - DefaultMessages.CODES)
    }

    @Test
    fun `ninguna plantilla se queda vacia`() {
        (DefaultMessages.EN + DefaultMessages.ES).forEach { (code, text) ->
            assertTrue(text.isNotBlank(), "plantilla vacía para '$code'")
        }
    }

    /** Los `{param}` de la plantilla tienen que existir de verdad en la violación. */
    @Test
    fun `los placeholders coinciden con los params de cada code`() {
        val esperados = mapOf(
            "size.min" to setOf("min"), "size.max" to setOf("max"),
            "min" to setOf("min"), "max" to setOf("max"),
            "range" to setOf("min", "max"),
            "decimalMin" to setOf("min"), "decimalMax" to setOf("max"),
            "digits" to setOf("integer", "fraction"),
        )
        // La `}` va escapada: Kotlin/JS compila con el flag `u` y ahí una llave suelta
        // es error de sintaxis, aunque en JVM funcione.
        val regex = Regex("\\{(\\w+)\\}")
        listOf("EN" to DefaultMessages.EN, "ES" to DefaultMessages.ES).forEach { (lang, mapa) ->
            mapa.forEach { (code, text) ->
                val usados = regex.findAll(text).map { it.groupValues[1] }.toSet()
                assertEquals(esperados[code] ?: emptySet(), usados, "$lang / $code")
            }
        }
    }

    @Test
    fun `interpola los parametros de la violacion`() {
        val es = DefaultMessageResolver(DefaultMessages.ES)
        assertEquals("el tamaño debe ser como máximo 80", es.resolve(Violation("name", "size.max", mapOf("max" to 80))))
        assertEquals("debe estar entre 18 y 120", es.resolve(Violation("age", "range", mapOf("min" to 18, "max" to 120))))
        assertEquals("no puede estar en blanco", es.resolve(Violation("name", "notBlank")))
    }

    @Test
    fun `el message explicito de la anotacion sigue ganando`() {
        val es = DefaultMessageResolver(DefaultMessages.ES)
        assertEquals("Requerido", es.resolve(Violation("name", "notBlank", message = "Requerido")))
    }

    @Test
    fun `se puede pisar una plantilla suelta sin copiar el mapa entero`() {
        val custom = DefaultMessageResolver(DefaultMessages.ES + mapOf("email" to "Correo inválido"))
        assertEquals("Correo inválido", custom.resolve(Violation("email", "email")))
        assertEquals("no puede estar en blanco", custom.resolve(Violation("name", "notBlank")))
    }

    @Test
    fun `forLanguage elige por prefijo y cae a ingles`() {
        assertEquals(DefaultMessages.ES, DefaultMessages.forLanguage("es"))
        assertEquals(DefaultMessages.ES, DefaultMessages.forLanguage("es-PE"))
        assertEquals(DefaultMessages.ES, DefaultMessages.forLanguage("ES"))
        assertEquals(DefaultMessages.EN, DefaultMessages.forLanguage("en-US"))
        assertEquals(DefaultMessages.EN, DefaultMessages.forLanguage("ja"), "un idioma sin traducir cae a EN, no al code")
    }

    @Test
    fun `ya no se devuelve el code crudo para un constraint conocido`() {
        // El motivo de todo esto: antes, sin configurar nada, esto devolvía "notBlank".
        val texto = DefaultMessageResolver(DefaultMessages.EN).resolve(Violation("name", "notBlank"))
        assertFalse(texto == "notBlank")
    }
}
