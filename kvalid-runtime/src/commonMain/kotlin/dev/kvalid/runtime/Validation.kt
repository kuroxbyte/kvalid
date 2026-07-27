package dev.kvalid.runtime

/**
 * Una violación de validación. Siempre lleva [code] estable + [params] (para i18n y lógica
 * programática); [message] es un texto opcional (estilo Jakarta `message = "..."`). Si el
 * constraint no declara message, queda `null` y la resolución a texto se hace por [code].
 */
public data class Violation(
    val path: String,
    val code: String,
    val params: Map<String, Any?> = emptyMap(),
    val message: String? = null,
)

/**
 * Validador reutilizable, estilo Jakarta pero **sin reflexión**: se implementa como `object`
 * y el código generado lo invoca directamente. Se enlaza a una anotación de constraint con
 * `@dev.kvalid.annotations.Constraint(validatedBy = ...)`.
 *
 * [T] es el tipo del valor validado: la propiedad (constraint a nivel de propiedad) o la
 * propia data class (constraint a nivel de clase, para reglas cross-field). El validador
 * empuja sus violaciones al [ctx] usando [field] como nombre base del path.
 *
 * ```
 * @Constraint(SlugValidator::class)
 * @Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
 * annotation class Slug
 *
 * object SlugValidator : ConstraintValidator<String> {
 *     override fun validate(value: String, field: String, ctx: ValidationContext) {
 *         if (!value.matches(SLUG)) ctx.violation(field, "slug")
 *     }
 * }
 * ```
 *
 * [params] lleva los argumentos (primitivos) de la anotación de constraint, para validadores
 * parametrizables (`@Length(max = 5)` → `params["max"]`). Vacío si la anotación no tiene args.
 */
public interface ConstraintValidator<T> {
    public fun validate(value: T, field: String, ctx: ValidationContext, params: Map<String, Any?> = emptyMap())
}

/**
 * Resultado de validar. **No es `Result<T>`**: una validación fallida no es una excepción;
 * construir un `Throwable` con stack trace en cada formulario es caro y falso.
 */
public sealed interface ValidationResult<out T> {
    public data class Valid<T>(val value: T) : ValidationResult<T>
    public data class Invalid(val violations: List<Violation>) : ValidationResult<Nothing>

    public val isValid: Boolean get() = this is Valid

    /** Transforma el valor si es válido; propaga las violaciones si no. */
    public fun <R> map(transform: (T) -> R): ValidationResult<R> = when (this) {
        is Valid -> Valid(transform(value))
        is Invalid -> this
    }

    /** El valor si es válido, o null. */
    public fun getOrNull(): T? = (this as? Valid)?.value

    /** Las violaciones (vacío si es válido). */
    public fun violationsOrEmpty(): List<Violation> = (this as? Invalid)?.violations ?: emptyList()
}

/** Excepción con las violaciones, para integrar con frameworks (Ktor, Spring...) por manejo de errores. */
public class ValidationException(
    public val violations: List<Violation>,
) : RuntimeException("Validación fallida: ${violations.size} violación(es)")

/** El valor si es válido; si no, lanza [ValidationException] con las violaciones. */
public fun <T> ValidationResult<T>.getOrThrow(): T = when (this) {
    is ValidationResult.Valid -> value
    is ValidationResult.Invalid -> throw ValidationException(violations)
}

/**
 * Acumula violaciones durante `validateCustom`. **Acumula, no aborta**. El [basePath] lo
 * fija el generado para que las violaciones de un objeto anidado lleven el prefijo del
 * padre (rebasing de paths de la cascada).
 */
public class ValidationContext(private val basePath: String = "") {
    private val _violations = mutableListOf<Violation>()
    public val violations: List<Violation> get() = _violations

    /** Añade una violación. [field] es relativo; se le antepone [basePath]. [field] vacío = el nodo base. */
    public fun violation(
        field: String,
        code: String,
        params: Map<String, Any?> = emptyMap(),
        message: String? = null,
    ) {
        val path = when {
            field.isEmpty() -> basePath
            basePath.isEmpty() -> field
            else -> "$basePath.$field"
        }
        _violations += Violation(path, code, params, message)
    }

    public fun violation(field: String, code: String, vararg params: Pair<String, Any?>) {
        violation(field, code, params.toMap())
    }
}
