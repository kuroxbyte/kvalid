package dev.kvalid.samples

import dev.kvalid.i18n.DefaultMessageResolver
import dev.kvalid.runtime.ValidationResult
import dev.kvalid.samples.javademo.JavaSamples

/**
 * Demo de consola. Muestra la variante **Kotlin (KSP)** (constraints, custom, element-level,
 * cross-field, i18n) y luego la variante **Java (APT)** ([JavaSamples]). Los ejemplos de
 * integración Ktor/Spring viven en `AppKtor.kt` / `AppSpring.kt` (Ktor con test end-to-end).
 */
fun main() {
    println("========== kvalid — variante KOTLIN (KSP) ==========\n")
    kotlinSamples()
    println("\n========== kvalid — variante JAVA (APT) ==========\n")
    JavaSamples.run()
}

private fun kotlinSamples() {
    // 1) Constraints básicos: acumula, no aborta.
    printResult("User válido", User("Ana", 30, "ana@x.com").validate())
    printResult("User inválido", User("", 15, "nope").validate())

    // 2) Constraint custom (@Slug → SlugValidator).
    printResult("Article slug válido", Article("mi-post").validate())
    printResult("Article slug inválido", Article("Mal Slug!").validate())

    // 3) Element-level (List<@NotBlank String>).
    printResult("Post tags", Post(listOf("kotlin", " ", "ksp")).validate())

    // 4) Validador de clase cross-field (@PasswordsMatch).
    printResult("Signup passwords", Signup("secret", "typo").validate())

    // 5) i18n: mismo code, mensajes localizados.
    val es = DefaultMessageResolver(
        mapOf(
            "notBlank" to "No puede estar vacío",
            "size.max" to "Máximo {max} caracteres",
            "min" to "Debe ser al menos {min}",
            "email" to "Email no válido",
        ),
    )
    println("\nMensajes i18n (es) para 'User inválido':")
    User("", 15, "nope").validate().violationsOrEmpty().forEach {
        println("  ${it.path}: ${es.resolve(it)}")
    }
}

private fun <T> printResult(label: String, result: ValidationResult<T>) {
    when (result) {
        is ValidationResult.Valid -> println("$label → OK")
        is ValidationResult.Invalid ->
            println("$label → " + result.violations.joinToString(", ") { "${it.path}=${it.code}" })
    }
}
