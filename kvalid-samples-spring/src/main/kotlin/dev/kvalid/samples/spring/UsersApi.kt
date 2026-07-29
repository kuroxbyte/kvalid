package dev.kvalid.samples.spring

import dev.kvalid.annotations.Email
import dev.kvalid.annotations.Min
import dev.kvalid.annotations.NotBlank
import dev.kvalid.annotations.Size
import dev.kvalid.annotations.Validated
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * Variante **Kotlin**: KSP genera `CreateUserRequest.validate()` y —por la opción
 * `kvalid.componentModel=spring`— un `CreateUserRequestKValidator` anotado `@Component`.
 */
@Validated
data class CreateUserRequest(
    @NotBlank @Size(max = 40) val name: String,
    @Email val email: String,
    @Min(18) val age: Int,
)

@RestController
class UsersController {

    /** `@Valid` y nada más: sin `validate()`, sin `getOrThrow()`, sin handler propio. */
    @PostMapping("/users")
    fun create(@Valid @RequestBody req: CreateUserRequest): Map<String, Any> =
        mapOf("created" to req.name, "age" to req.age)
}
