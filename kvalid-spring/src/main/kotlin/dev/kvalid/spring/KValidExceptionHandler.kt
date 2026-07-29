package dev.kvalid.spring

import dev.kvalid.runtime.ValidationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Cuerpo de error (serializado por Jackson). */
public data class ValidationErrorResponse(val errors: List<ValidationError>)

public data class ValidationError(
    val path: String,
    val code: String,
    val message: String? = null,
)

/**
 * `@RestControllerAdvice` que convierte una [ValidationException] en un **400** con las
 * violaciones. El usuario valida en su controller con
 * `dto.validate().getOrThrow()` y Spring enruta el fallo aquí.
 *
 * Registrar como bean (component scan o `@Import(KValidExceptionHandler::class)`).
 */
@RestControllerAdvice
public open class KValidExceptionHandler {

    @ExceptionHandler(ValidationException::class)
    public open fun handle(ex: ValidationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ValidationErrorResponse(ex.violations.map { ValidationError(it.path, it.code, it.message) }))
}
