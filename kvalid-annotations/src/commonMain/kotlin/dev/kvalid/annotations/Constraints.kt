package dev.kvalid.annotations

import kotlin.reflect.KClass

/**
 * Marca una data class para generar su `validate()`. En el tipo de una propiedad,
 * activa la cascada automática (validación anidada).
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class Validated

/**
 * Meta-anotación que enlaza una anotación de constraint con su validador reutilizable
 * (estilo Jakarta `@Constraint(validatedBy = ...)`, pero sin reflexión: el validador es un
 * `object` que implementa `dev.kvalid.runtime.ConstraintValidator<T>` y el generado lo llama
 * directamente). Puede ponerse en una anotación con `@Target(PROPERTY)` (valida la propiedad)
 * o `@Target(CLASS)` (reglas cross-field: `T` es la propia data class).
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class Constraint(val validatedBy: KClass<*>)

// ── Texto ──────────────────────────────────────────────────────────────────────

/** No en blanco (String). `code = "notBlank"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class NotBlank(val message: String = "")

/** No vacío (String o colección). `code = "notEmpty"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class NotEmpty(val message: String = "")

/** Longitud (String) o tamaño (colección) en `[min, max]`. `code = "size.min"`/`"size.max"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Size(val min: Int = 0, val max: Int = Int.MAX_VALUE, val message: String = "")

/** Coincide con la regex (compilada UNA vez a nivel de archivo). `code = "pattern"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Pattern(val regex: String, val message: String = "")

/** Email (validación razonable por regex, no RFC completo). `code = "email"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Email(val message: String = "")

/** URL http/https (validación razonable por regex). `code = "url"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Url(val message: String = "")

/** El valor (String) debe estar entre [values]. `code = "oneOf"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class OneOf(vararg val values: String, val message: String = "")

// ── Numérico ───────────────────────────────────────────────────────────────────

/** Valor mínimo (inclusive). `code = "min"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Min(val value: Long, val message: String = "")

/** Valor máximo (inclusive). `code = "max"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Max(val value: Long, val message: String = "")

/** Rango `[min, max]` inclusive. `code = "range"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Range(val min: Long, val max: Long, val message: String = "")

/** Mínimo decimal exacto (inclusive), como String para no perder precisión. `code = "decimalMin"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class DecimalMin(val value: String, val message: String = "")

/** Máximo decimal exacto (inclusive), como String. `code = "decimalMax"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class DecimalMax(val value: String, val message: String = "")

/** Estrictamente positivo (> 0). `code = "positive"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Positive(val message: String = "")

/** Estrictamente negativo (< 0). `code = "negative"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Negative(val message: String = "")

// ── Temporal (Instant) ─────────────────────────────────────────────────────────

/** Debe ser anterior a ahora. Aplica a `kotlinx.datetime.Instant` o `java.time.Instant`. `code = "past"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Past(val message: String = "")

/** Debe ser posterior a ahora. Aplica a `kotlinx.datetime.Instant` o `java.time.Instant`. `code = "future"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class Future(val message: String = "")

// ── Presencia ────────────────────────────────────────────────────────────────

/** No nulo (para tipos que no lo garantizan, p. ej. plataformas Java). `code = "notNull"`. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
public annotation class NotNull(val message: String = "")
