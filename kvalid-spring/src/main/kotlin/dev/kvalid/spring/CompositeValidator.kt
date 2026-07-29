package dev.kvalid.spring

import org.springframework.validation.Errors
import org.springframework.validation.SmartValidator
import org.springframework.validation.Validator

/**
 * Encadena varios [Validator] sobre el mismo objeto, **acumulando** los errores de todos.
 *
 * Existe por un riesgo concreto: registrar el validador de kvalid como el global de Spring
 * **apagaría** Jakarta Bean Validation (Hibernate Validator) si el usuario también la usa —
 * sus `@NotNull` dejarían de aplicarse en silencio. Con este composite conviven: cada
 * delegado valida lo que declara soportar y los errores se suman en el mismo `BindingResult`.
 *
 * El orden importa solo para el orden de los mensajes; ningún delegado corta a los demás.
 */
public class CompositeValidator(
    /** Los delegados, en orden. Público para inspección/diagnóstico. */
    public val delegates: List<Validator>,
) : SmartValidator {

    /** Soporta el tipo si **alguno** de los delegados lo soporta. */
    override fun supports(clazz: Class<*>): Boolean = delegates.any { it.supports(clazz) }

    override fun validate(target: Any, errors: Errors) {
        supporting(target).forEach { it.validate(target, errors) }
    }

    /** Propaga los *hints* solo a los delegados que sepan usarlos (`SmartValidator`). */
    override fun validate(target: Any, errors: Errors, vararg validationHints: Any) {
        supporting(target).forEach { delegate ->
            if (delegate is SmartValidator) delegate.validate(target, errors, *validationHints)
            else delegate.validate(target, errors)
        }
    }

    private fun supporting(target: Any): List<Validator> =
        delegates.filter { it.supports(target.javaClass) }
}
