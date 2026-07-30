package dev.kvalid.it

import dev.kvalid.annotations.AssertFalse
import dev.kvalid.annotations.AssertTrue
import dev.kvalid.annotations.DecimalMax
import dev.kvalid.annotations.DecimalMin
import dev.kvalid.annotations.Digits
import dev.kvalid.annotations.Email
import dev.kvalid.annotations.Future
import dev.kvalid.annotations.FutureOrPresent
import dev.kvalid.annotations.Max
import dev.kvalid.annotations.Min
import dev.kvalid.annotations.Negative
import dev.kvalid.annotations.NegativeOrZero
import dev.kvalid.annotations.NotBlank
import dev.kvalid.annotations.NotEmpty
import dev.kvalid.annotations.NotNull
import dev.kvalid.annotations.Null
import dev.kvalid.annotations.OneOf
import dev.kvalid.annotations.Past
import dev.kvalid.annotations.PastOrPresent
import dev.kvalid.annotations.Pattern
import dev.kvalid.annotations.Positive
import dev.kvalid.annotations.PositiveOrZero
import dev.kvalid.annotations.Range
import dev.kvalid.annotations.Size
import dev.kvalid.annotations.Url
import dev.kvalid.annotations.Validated
import java.time.Instant

/**
 * Un campo por constraint incorporado, para que una sola llamada a `validate()` produzca
 * **todos** los `code` que el generador sabe emitir.
 *
 * Existe para [dev.kvalid.it.MessageCoverageTest]: es la forma de comprobar que
 * `DefaultMessages` cubre lo que el generador emite de verdad, y no una lista escrita a mano
 * que se queda desfasada al añadir un constraint.
 *
 * Los constraints que se contradicen (`@NotNull`/`@Null`, `@Positive`/`@Negative`) van en
 * campos distintos a propósito.
 */
@Validated
data class Coverage(
    @NotNull val notNullField: String?,
    @Null val nullField: String?,
    @NotBlank val notBlankField: String,
    @NotEmpty val notEmptyField: List<String>,
    @Size(min = 3) val sizeMinField: String,
    @Size(max = 2) val sizeMaxField: String,
    @Pattern(regex = "[0-9]+") val patternField: String,
    @Email val emailField: String,
    @Url val urlField: String,
    @OneOf("A", "B") val oneOfField: String,
    @Min(10) val minField: Int,
    @Max(10) val maxField: Int,
    @Range(min = 5, max = 9) val rangeField: Int,
    @DecimalMin("1.5") val decimalMinField: Double,
    @DecimalMax("1.5") val decimalMaxField: Double,
    @Digits(integer = 2, fraction = 1) val digitsField: String,
    @Positive val positiveField: Int,
    @Negative val negativeField: Int,
    @PositiveOrZero val positiveOrZeroField: Int,
    @NegativeOrZero val negativeOrZeroField: Int,
    @AssertTrue val assertTrueField: Boolean,
    @AssertFalse val assertFalseField: Boolean,
    @Past val pastField: Instant,
    @Future val futureField: Instant,
    @PastOrPresent val pastOrPresentField: Instant,
    @FutureOrPresent val futureOrPresentField: Instant,
)
