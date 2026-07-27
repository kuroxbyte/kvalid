package dev.kvalid.samples

import dev.kvalid.annotations.Constraint
import dev.kvalid.annotations.Email
import dev.kvalid.annotations.Min
import dev.kvalid.annotations.NotBlank
import dev.kvalid.annotations.Size
import dev.kvalid.annotations.Validated
import dev.kvalid.runtime.ConstraintValidator
import dev.kvalid.runtime.ValidationContext

/**
 * Modelo de dominio Kotlin validado por KSP. Cada `@Validated` genera una extensión
 * `Type.validate(): ValidationResult<Type>` en `<Type>Validator.kt` (mismo paquete).
 */
@Validated
data class User(
    @NotBlank @Size(max = 20) val name: String,
    @Min(18) val age: Int,
    @Email val email: String,
)

// ── Constraint custom (estilo Jakarta, sin reflexión) ────────────────────────────

/** Anotación de constraint enlazada a su validador con `@Constraint`. */
@Constraint(SlugValidator::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Slug

/** Validador reutilizable: un `object` que implementa `ConstraintValidator`. */
object SlugValidator : ConstraintValidator<String> {
    private val SLUG = Regex("[a-z0-9-]+")
    override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (!SLUG.matches(value)) ctx.violation(field, "slug")
    }
}

@Validated
data class Article(@Slug val slug: String)

// ── Element-level: constraints sobre los elementos de una colección ──────────────

@Validated
data class Post(val tags: List<@NotBlank String>)

// ── Validador de clase (cross-field) ─────────────────────────────────────────────

@Constraint(PasswordsMatchValidator::class)
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PasswordsMatch

object PasswordsMatchValidator : ConstraintValidator<Signup> {
    override fun validate(value: Signup, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (value.password != value.confirm) ctx.violation("confirm", "passwordsMatch")
    }
}

@Validated
@PasswordsMatch
data class Signup(val password: String, val confirm: String)
