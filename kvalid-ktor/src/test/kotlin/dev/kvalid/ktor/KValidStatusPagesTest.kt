package dev.kvalid.ktor

import dev.kvalid.runtime.ValidationException
import dev.kvalid.runtime.Violation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KValidStatusPagesTest {

    @Test
    fun `una ValidationException se convierte en 400 con las violaciones`() = testApplication {
        install(ContentNegotiation) { json() }
        install(StatusPages) { kvalid() }
        routing {
            get("/bad") {
                throw ValidationException(listOf(Violation("name", "notBlank", message = "requerido")))
            }
            get("/ok") { call.respondText("ok") }
        }

        val bad = client.get("/bad")
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        val body = bad.bodyAsText()
        assertTrue("\"path\":\"name\"" in body, body)
        assertTrue("\"code\":\"notBlank\"" in body, body)
        assertTrue("requerido" in body, body)

        assertEquals(HttpStatusCode.OK, client.get("/ok").status)
    }
}
