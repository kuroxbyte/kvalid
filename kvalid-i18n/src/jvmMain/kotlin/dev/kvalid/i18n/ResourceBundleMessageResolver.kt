package dev.kvalid.i18n

import dev.kvalid.runtime.Violation
import java.util.ResourceBundle

/**
 * Resolutor JVM que toma las plantillas de un `ResourceBundle` (archivos `.properties` por
 * locale). La clave es el `code` de la violación; se interpola `{param}`. Precedencia:
 * `violation.message` explícito → entrada del bundle → [fallback] (por defecto, el `code`).
 *
 * ```
 * val es = ResourceBundleMessageResolver(ResourceBundle.getBundle("messages", Locale("es")))
 * ```
 */
public class ResourceBundleMessageResolver(
    private val bundle: ResourceBundle,
    private val fallback: (Violation) -> String = { it.code },
) : MessageResolver {
    override fun resolve(violation: Violation): String {
        violation.message?.let { return interpolate(it, violation.params) }
        val template = if (bundle.containsKey(violation.code)) bundle.getString(violation.code) else null
        return template?.let { interpolate(it, violation.params) } ?: fallback(violation)
    }
}
