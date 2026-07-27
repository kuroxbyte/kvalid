package dev.kvalid.samples

import dev.kvalid.runtime.getOrThrow
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Controlador Spring de ejemplo. Registrando el `@RestControllerAdvice` de kvalid-spring
 * (`dev.kvalid.spring.KvalidExceptionHandler`) como bean — por component-scan o
 * `@Import(KvalidExceptionHandler::class)` — cualquier `ValidationException` que escape del
 * controlador se traduce a un **400** con los errores. El controlador solo llama
 * `validate().getOrThrow()`.
 */
@RestController
open class UserController {

    @PostMapping("/users")
    open fun create(@RequestBody req: UserRequest): Map<String, String> {
        val user = User(req.name, req.age, req.email).validate().getOrThrow()
        return mapOf("created" to user.name)
    }
}
