package dev.kvalid.samples

import dev.kvalid.ktor.kvalid
import dev.kvalid.runtime.getOrThrow
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/** DTO de entrada (serializable para Ktor); se valida con la extensión generada. */
@Serializable
data class UserRequest(val name: String, val age: Int, val email: String)

/**
 * Módulo Ktor de ejemplo. `StatusPages { kvalid() }` convierte cualquier [ValidationException]
 * (lanzada por `getOrThrow()`) en un **400** con el cuerpo de errores. El endpoint valida el
 * request y, si pasa, responde 200.
 */
fun Application.kvalidModule() {
    install(ContentNegotiation) { json() }
    install(StatusPages) { kvalid() } // ← integración kvalid-ktor

    routing {
        post("/users") {
            val req = call.receive<UserRequest>()
            // Reusa el mismo modelo de dominio validado.
            val user = User(req.name, req.age, req.email).validate().getOrThrow()
            call.respond(mapOf("created" to user.name))
        }
    }
}
