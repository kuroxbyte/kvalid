package dev.kvalid.i18n

import dev.kvalid.runtime.Violation
import java.util.Locale
import java.util.ResourceBundle
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceBundleMessageResolverTest {

    private val resolver = ResourceBundleMessageResolver(
        ResourceBundle.getBundle("kvalidmsg", Locale.ROOT),
    )

    @Test
    fun `resuelve desde el bundle e interpola params`() {
        assertEquals("Máximo 80 caracteres", resolver.resolve(Violation("name", "size.max", mapOf("max" to 80))))
        assertEquals("No puede estar vacío", resolver.resolve(Violation("name", "notBlank")))
    }

    @Test
    fun `code ausente en el bundle cae al fallback`() {
        assertEquals("weird", resolver.resolve(Violation("x", "weird")))
    }
}
