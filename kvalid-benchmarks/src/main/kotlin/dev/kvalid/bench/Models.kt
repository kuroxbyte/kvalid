package dev.kvalid.bench

import dev.kvalid.annotations.Email
import dev.kvalid.annotations.NotBlank
import dev.kvalid.annotations.Range
import dev.kvalid.annotations.Size
import dev.kvalid.annotations.Validated

/** Modelo validado por kvalid (codegen, cero reflexión). Genera `KvalidUser.validate()`. */
@Validated
data class KvalidUser(
    @NotBlank @Size(max = 80) val name: String,
    @Range(min = 18, max = 120) val age: Int,
    @Email val email: String,
)

/** Modelo equivalente validado por Hibernate Validator (reflexión). */
data class HibernateUser(
    @field:jakarta.validation.constraints.NotBlank
    @field:jakarta.validation.constraints.Size(max = 80)
    val name: String,
    @field:jakarta.validation.constraints.Min(18)
    @field:jakarta.validation.constraints.Max(120)
    val age: Int,
    @field:jakarta.validation.constraints.Email
    val email: String,
)
