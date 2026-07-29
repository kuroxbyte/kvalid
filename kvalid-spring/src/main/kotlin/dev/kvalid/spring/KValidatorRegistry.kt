package dev.kvalid.spring

import dev.kvalid.runtime.spi.KValidator

/**
 * Índice `Class → KValidator` de los adaptadores generados.
 *
 * En Spring **no hace falta `ServiceLoader`**: los adaptadores se generan con
 * `@Component` (opción de KSP `kvalid.componentModel=spring`) y el contenedor inyecta
 * `List<KValidator<*>>` ya resuelta. Ventaja añadida: **Spring AOT** los registra para
 * native-image, cosa que `ServiceLoader` no da gratis.
 *
 * El lookup es un `Map` — **no reflexión**. La validación real sigue siendo código generado.
 */
public class KValidatorRegistry(validators: List<KValidator<*>>) {

    private val byType: Map<Class<*>, KValidator<*>> = validators.associateBy { it.type }

    /** El adaptador de [type], o `null` si ese tipo no es `@Validated`. */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> forType(type: Class<T>): KValidator<T>? =
        byType[type] as KValidator<T>?

    /**
     * Si hay validador para [type]. Coincidencia **exacta** de clase: los tipos `@Validated`
     * son data classes (finales), así que no se recorre la jerarquía — evita sorpresas con
     * proxies y subclases.
     */
    public fun supports(type: Class<*>): Boolean = byType.containsKey(type)

    /** Cuántos adaptadores hay registrados (diagnóstico y tests). */
    public val size: Int get() = byType.size
}
