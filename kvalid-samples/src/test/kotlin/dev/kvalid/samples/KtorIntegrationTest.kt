package dev.kvalid.samples

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica end-to-end que la integración kvalid-ktor traduce las violaciones a un 400. */
class KtorIntegrationTest {

    @Test
    fun `request valido responde 200`() = testApplication {
        application { kvalidModule() }
        val res = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Ana","age":30,"email":"ana@x.com"}""")
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }

    @Test
    fun `request invalido responde 400 con errores`() = testApplication {
        application { kvalidModule() }
        val res = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"","age":15,"email":"nope"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, res.status)
        val body = res.bodyAsText()
        assertTrue("notBlank" in body, "esperaba el code notBlank en $body")
        assertTrue("min" in body, "esperaba el code min en $body")
    }
}
