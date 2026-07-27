package dev.kvalid.i18n

import dev.kvalid.runtime.Violation
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageResolverTest {

    private val es = DefaultMessageResolver(
        mapOf(
            "notBlank" to "No puede estar vacío",
            "size.max" to "Máximo {max} caracteres",
            "range" to "Debe estar entre {min} y {max}",
        ),
    )

    @Test
    fun `interpola params en la plantilla`() {
        assertEquals("Máximo 80 caracteres", es.resolve(Violation("name", "size.max", mapOf("max" to 80))))
        assertEquals("Debe estar entre 18 y 120", es.resolve(Violation("age", "range", mapOf("min" to 18, "max" to 120))))
    }

    @Test
    fun `sin params usa la plantilla tal cual`() {
        assertEquals("No puede estar vacío", es.resolve(Violation("name", "notBlank")))
    }

    @Test
    fun `el message explicito gana sobre la plantilla`() {
        assertEquals("Requerido", es.resolve(Violation("name", "notBlank", message = "Requerido")))
    }

    @Test
    fun `code desconocido cae al fallback que es el code`() {
        assertEquals("weird.code", es.resolve(Violation("x", "weird.code")))
    }
}
