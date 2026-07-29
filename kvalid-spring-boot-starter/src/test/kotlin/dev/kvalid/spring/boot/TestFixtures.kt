package dev.kvalid.spring.boot

import dev.kvalid.runtime.ValidationResult
import dev.kvalid.runtime.Violation
import dev.kvalid.runtime.spi.KvalidValidator
import org.springframework.stereotype.Component

/** DTO de entrada del endpoint bajo prueba. */
public data class CreateUser(val name: String, val email: String, val age: Int)

/**
 * Escrito a mano, pero **idéntico en forma al que emite el processor** con
 * `kvalid.componentModel=spring` (ver `ValidatorAdapterTest`): un `@Component` que delega.
 *
 * Aquí se prueba el **cableado con Spring**, no el codegen — por eso no hace falta correr KSP.
 */
@Component
public class CreateUserKvalidValidator : KvalidValidator<CreateUser> {

    override val type: Class<CreateUser> = CreateUser::class.java

    override fun validate(value: CreateUser): ValidationResult<CreateUser> {
        val violations = buildList {
            if (value.name.isBlank()) add(Violation("name", "notBlank"))
            if (!value.email.contains('@')) add(Violation("email", "email"))
            if (value.age < 18) add(Violation("age", "min", mapOf("min" to 18)))
        }
        return if (violations.isEmpty()) ValidationResult.Valid(value) else ValidationResult.Invalid(violations)
    }
}
