package dev.kvalid.core.model

import dev.genkit.model.TypeRef

/** FQN de las anotaciones de kvalid y del runtime (se leen por nombre desde el modelo). */
public object ValidationNames {
    public const val VALIDATED: String = "dev.kvalid.annotations.Validated"
    public const val CONSTRAINT: String = "dev.kvalid.annotations.Constraint"
    public const val NOT_BLANK: String = "dev.kvalid.annotations.NotBlank"
    public const val NOT_EMPTY: String = "dev.kvalid.annotations.NotEmpty"
    public const val SIZE: String = "dev.kvalid.annotations.Size"
    public const val PATTERN: String = "dev.kvalid.annotations.Pattern"
    public const val EMAIL: String = "dev.kvalid.annotations.Email"
    public const val URL: String = "dev.kvalid.annotations.Url"
    public const val ONE_OF: String = "dev.kvalid.annotations.OneOf"
    public const val MIN: String = "dev.kvalid.annotations.Min"
    public const val MAX: String = "dev.kvalid.annotations.Max"
    public const val RANGE: String = "dev.kvalid.annotations.Range"
    public const val DECIMAL_MIN: String = "dev.kvalid.annotations.DecimalMin"
    public const val DECIMAL_MAX: String = "dev.kvalid.annotations.DecimalMax"
    public const val PAST: String = "dev.kvalid.annotations.Past"
    public const val FUTURE: String = "dev.kvalid.annotations.Future"
    public const val POSITIVE: String = "dev.kvalid.annotations.Positive"
    public const val NEGATIVE: String = "dev.kvalid.annotations.Negative"
    public const val NOT_NULL: String = "dev.kvalid.annotations.NotNull"
}

public object ValidationDiagnostics {
    /** Constraint aplicada a un tipo incompatible (p. ej. @Range sobre String). */
    public const val CONSTRAINT_TYPE: String = "kvalid.constraint.type"

    /** Argumentos de constraint inválidos (min>max, tamaño negativo, regex/decimal mal formado). */
    public const val CONSTRAINT_ARGS: String = "kvalid.constraint.args"
}

/** Un constraint resuelto sobre una propiedad. [message] vacío = usar solo el `code`. */
public sealed interface Constraint {
    public val message: String

    public data class NotBlank(override val message: String = "") : Constraint
    public data class NotEmpty(override val message: String = "") : Constraint
    public data class Size(val min: Int, val max: Int, override val message: String = "") : Constraint
    public data class Pattern(val regex: String, override val message: String = "") : Constraint
    public data class Email(override val message: String = "") : Constraint
    public data class Url(override val message: String = "") : Constraint
    public data class OneOf(val values: List<String>, override val message: String = "") : Constraint
    public data class Min(val value: Long, override val message: String = "") : Constraint
    public data class Max(val value: Long, override val message: String = "") : Constraint
    public data class Range(val min: Long, val max: Long, override val message: String = "") : Constraint
    public data class DecimalMin(val value: String, override val message: String = "") : Constraint
    public data class DecimalMax(val value: String, override val message: String = "") : Constraint
    public data class Past(override val message: String = "") : Constraint
    public data class Future(override val message: String = "") : Constraint
    public data class Positive(override val message: String = "") : Constraint
    public data class Negative(override val message: String = "") : Constraint
    public data class NotNull(override val message: String = "") : Constraint

    /**
     * Constraint reutilizable definido por el usuario: una anotación meta-anotada con
     * `@Constraint(validatedBy = V)`. [validatorFqn] es el `object` `ConstraintValidator<T>`
     * a invocar sobre la propiedad.
     */
    public data class Custom(
        val validatorFqn: String,
        val params: Map<String, Any?> = emptyMap(),
        override val message: String = "",
    ) : Constraint
}

public data class ValidatedField(
    val name: String,
    val type: TypeRef,
    val constraints: List<Constraint>,
    /** El tipo de la propiedad es `@Validated` → cascada. */
    val cascade: Boolean,
    /** Constraints sobre los ELEMENTOS de una colección (`List<@NotBlank String>`). */
    val elementConstraints: List<Constraint> = emptyList(),
    /** El tipo del ELEMENTO de la colección es `@Validated` → cascada por elemento (`campo[i].x`). */
    val elementCascade: Boolean = false,
)

/**
 * Validador a nivel de clase (reglas cross-field): un `object` `ConstraintValidator<TheClass>`
 * referenciado por una anotación de clase meta-anotada con `@Constraint`.
 */
public data class ClassValidator(val validatorFqn: String, val params: Map<String, Any?> = emptyMap())

public data class ValidationModel(
    val type: TypeRef,
    val fields: List<ValidatedField>,
    /** Validadores cross-field (reemplazan la antigua convención `validateCustom`). */
    val classValidators: List<ClassValidator> = emptyList(),
)
