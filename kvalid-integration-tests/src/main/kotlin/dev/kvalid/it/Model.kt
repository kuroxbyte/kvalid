package dev.kvalid.it

import dev.kvalid.annotations.AssertFalse
import dev.kvalid.annotations.AssertTrue
import dev.kvalid.annotations.Constraint
import dev.kvalid.annotations.Digits
import dev.kvalid.annotations.Email
import dev.kvalid.annotations.FutureOrPresent
import dev.kvalid.annotations.Min
import dev.kvalid.annotations.NegativeOrZero
import dev.kvalid.annotations.NotBlank
import dev.kvalid.annotations.Null
import dev.kvalid.annotations.PastOrPresent
import dev.kvalid.annotations.PositiveOrZero
import dev.kvalid.annotations.Size
import dev.kvalid.annotations.Validated
import dev.kvalid.runtime.ConstraintValidator
import dev.kvalid.runtime.ValidationContext
import java.time.Instant

/** Dominio real consumido por KSP. Cada `@Validated` genera `Type.validate(): ValidationResult<Type>`. */
@Validated
data class User(
    @NotBlank @Size(max = 20) val name: String,
    @Min(18) val age: Int,
    @Email val email: String,
)

// Constraint custom (object ConstraintValidator, invocado sin reflexión desde el generado).
@Constraint(SlugValidator::class)
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Slug

object SlugValidator : ConstraintValidator<String> {
    private val SLUG = Regex("[a-z0-9-]+")
    override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (!SLUG.matches(value)) ctx.violation(field, "slug")
    }
}

@Validated
data class Article(@Slug val slug: String, val tags: List<@NotBlank String>)

// Validador de clase (cross-field).
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

/** Los constraints alineados con Jakarta añadidos en 0.3.0, consumidos de verdad por KSP. */
@Validated
data class Enrolment(
    @AssertTrue val acceptedTerms: Boolean,
    @AssertFalse val banned: Boolean,
    @PositiveOrZero val credits: Int,
    @NegativeOrZero val balance: Int,
    @Digits(integer = 4, fraction = 2) val amount: String,
    @Null val adminNote: String?,
    @PastOrPresent val createdAt: Instant,
    @FutureOrPresent val expiresAt: Instant,
)
