package dev.kvalid.spring

import dev.kvalid.runtime.Violation
import org.springframework.beans.BeansException
import org.springframework.validation.Errors
import org.springframework.validation.SmartValidator

/**
 * Puente entre kvalid y el SPI **propio de Spring** (`org.springframework.validation.Validator`).
 *
 * Es el SPI que alimenta `@Valid`/`@Validated`, así que registrarlo hace que
 * `@Valid @RequestBody` funcione **nativamente y con un solo adaptador en los dos stacks**
 * (Spring MVC y WebFlux). Las violaciones entran en `Errors`/`BindingResult`, de modo que
 * Spring lanza `MethodArgumentNotValidException` (MVC) o `WebExchangeBindException` (WebFlux)
 * y **Boot ya responde 400** — sin que el usuario escriba un handler.
 *
 * Los `code` de kvalid (`notBlank`, `size.max`) alimentan la resolución de mensajes de Spring:
 * `MessageSource` prueba `code.objeto.campo`, `code.campo`, `code.tipo`, `code`.
 */
public class KvalidSpringValidator(
    private val registry: KvalidValidatorRegistry,
) : SmartValidator {

    override fun supports(clazz: Class<*>): Boolean = registry.supports(clazz)

    override fun validate(target: Any, errors: Errors) {
        val validator = registry.forType(target.javaClass) ?: return
        validator.validate(target).violationsOrEmpty().forEach { violation ->
            reject(errors, violation)
        }
    }

    /**
     * kvalid no tiene *groups*: los `validationHints` de `@Validated(Group::class)` se ignoran
     * y se valida igual. Es preferible a no validar (fallar abierto sería peor).
     */
    override fun validate(target: Any, errors: Errors, vararg validationHints: Any) {
        validate(target, errors)
    }

    private fun reject(errors: Errors, violation: Violation) {
        // El orden de `params` es el de declaración (mapOf preserva inserción), así que sirve
        // como args posicionales {0}, {1}… del MessageSource.
        val args = violation.params.values.toTypedArray()

        // SIEMPRE un defaultMessage no nulo. En kvalid `message` es opcional (el contrato es
        // code + params), pero Spring resuelve los errores como MessageSourceResolvable al
        // renderizar el ProblemDetail: sin defaultMessage y sin entrada en el MessageSource
        // lanza NoSuchMessageException y el cuerpo del 400 se pierde (se va vacío).
        // El código es el fallback natural; con un MessageSource se sigue traduciendo por él.
        val message = violation.message ?: violation.code

        if (violation.path.isBlank()) {
            errors.reject(violation.code, args, message) // cross-field / nivel de clase
            return
        }
        try {
            errors.rejectValue(violation.path, violation.code, args, message)
        } catch (e: BeansException) {
            // El path no es una propiedad legible del target: pasa con validadores custom que
            // inventan un path sintético. Degradar a error global es preferible a un 500.
            errors.reject(violation.code, args, "${violation.path}: $message")
        } catch (e: IndexOutOfBoundsException) {
            // Path indexado (`tags[3]`) que ya no resuelve contra el objeto enlazado.
            errors.reject(violation.code, args, "${violation.path}: $message")
        }
    }
}
