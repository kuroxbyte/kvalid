package dev.kvalid.spring

import dev.kvalid.runtime.ValidationException
import dev.kvalid.runtime.Violation
import org.springframework.http.HttpStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class KValidExceptionHandlerTest {

    @Test
    fun `mapea ValidationException a un 400 con las violaciones`() {
        val resp = KValidExceptionHandler().handle(
            ValidationException(listOf(Violation("name", "notBlank", message = "requerido"))),
        )
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        val error = resp.body!!.errors.single()
        assertEquals("name", error.path)
        assertEquals("notBlank", error.code)
        assertEquals("requerido", error.message)
    }
}
