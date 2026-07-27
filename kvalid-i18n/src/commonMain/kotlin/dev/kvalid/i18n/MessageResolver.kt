package dev.kvalid.i18n

import dev.kvalid.runtime.Violation

/** Resuelve una [Violation] (code + params) a texto legible. KMP, sin reflexión. */
public fun interface MessageResolver {
    public fun resolve(violation: Violation): String
}

/**
 * Resolutor por mapa de plantillas `code -> plantilla`, con interpolación de `{param}` desde
 * `violation.params`. Precedencia: el [Violation.message] explícito gana; luego la plantilla
 * del [templates] para el `code`; si no hay, [fallback] (por defecto, el propio `code`).
 *
 * ```
 * val es = DefaultMessageResolver(mapOf(
 *     "notBlank" to "No puede estar vacío",
 *     "size.max" to "Máximo {max} caracteres",
 *     "range"    to "Debe estar entre {min} y {max}",
 * ))
 * es.resolve(Violation("name", "size.max", mapOf("max" to 80)))  // "Máximo 80 caracteres"
 * ```
 */
public class DefaultMessageResolver(
    private val templates: Map<String, String>,
    private val fallback: (Violation) -> String = { it.code },
) : MessageResolver {
    override fun resolve(violation: Violation): String {
        violation.message?.let { return interpolate(it, violation.params) }
        val template = templates[violation.code] ?: return fallback(violation)
        return interpolate(template, violation.params)
    }
}

/** Interpola `{param}` en [template] con [params]. Compartida por los resolutores. */
public fun interpolate(template: String, params: Map<String, Any?>): String {
    if (params.isEmpty() || '{' !in template) return template
    var result = template
    for ((key, value) in params) {
        result = result.replace("{$key}", value.toString())
    }
    return result
}
