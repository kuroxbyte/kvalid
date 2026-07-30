package dev.kvalid.ktor

import dev.kvalid.runtime.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

/** Cuerpo de respuesta de error de validación (serializable con content negotiation). */
@Serializable
public data class ValidationErrorResponse(val errors: List<ValidationError>)

@Serializable
public data class ValidationError(
    val path: String,
    val code: String,
    val message: String? = null,
)

/**
 * Registra el manejo de [ValidationException] en `StatusPages`: responde [status] (400 por
 * defecto) con las violaciones. El usuario valida en su ruta con
 * `call.receive<Dto>().validate().getOrThrow()` y esto convierte el fallo en una respuesta.
 *
 * ```
 * install(ContentNegotiation) { json() }
 * install(StatusPages) { kvalid() }
 * ```
 *
 * Requiere ContentNegotiation configurado (kotlinx.serialization JSON) para serializar el cuerpo.
 */
public fun StatusPagesConfig.kvalid(
    status: HttpStatusCode = HttpStatusCode.BadRequest,
    /**
     * Texto para las violaciones sin `message` propio. Por defecto `null` (solo `path` +
     * `code`); pásale `DefaultMessageResolver(DefaultMessages.ES)::resolve` —o tu propio
     * resolutor— para que el cuerpo lleve texto legible.
     */
    fallbackMessage: ((dev.kvalid.runtime.Violation) -> String)? = null,
) {
    exception<ValidationException> { call, cause ->
        call.respond(
            status,
            ValidationErrorResponse(
                cause.violations.map {
                    ValidationError(it.path, it.code, it.message ?: fallbackMessage?.invoke(it))
                },
            ),
        )
    }
}
