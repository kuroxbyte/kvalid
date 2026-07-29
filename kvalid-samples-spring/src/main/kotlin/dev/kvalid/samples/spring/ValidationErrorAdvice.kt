package dev.kvalid.samples.spring

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * **Opcional pero recomendado.** Sin esto igual obtienes un **400**, pero el `ProblemDetail`
 * por defecto de Boot no incluye qué campos fallaron: el cuerpo es genérico
 * (`{"title":"Bad Request","detail":"Invalid request content."}`).
 *
 * Los errores sí están en el `BindingResult` — este advice de ~10 líneas los expone. Es
 * exactamente lo mismo que harías con Jakarta Bean Validation: no es específico de KValid.
 *
 * En **WebFlux** la excepción es `WebExchangeBindException` (subclase de `BindException`);
 * cambia solo el tipo del `@ExceptionHandler`.
 */
@RestControllerAdvice
class ValidationErrorAdvice {

    data class FieldErrorDto(val field: String, val code: String, val message: String?)
    data class ErrorsDto(val errors: List<FieldErrorDto>)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onInvalid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorsDto> =
        ResponseEntity.badRequest().body(
            ErrorsDto(
                ex.bindingResult.fieldErrors.map { error ->
                    // `code` es el de KValid (notBlank, size.max, min…): estable y apto para i18n.
                    FieldErrorDto(error.field, error.code ?: "invalid", error.defaultMessage)
                },
            ),
        )
}
