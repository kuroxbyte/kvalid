package dev.kvalid.runtime.spi

import dev.kvalid.runtime.ValidationResult
import java.util.ServiceLoader

/**
 * Adaptador **tipado** sobre el `validate()` generado, para bordes que reciben un `Any` y
 * necesitan encontrar "el validador de ESTE tipo" (el `Validator` de Spring, un filtro HTTP,
 * un listener). El generado de kvalid es una *extension function*
 * (`fun T.validate(): ValidationResult<T>`), que **no se puede despachar genéricamente**:
 * esta interfaz cierra ese hueco.
 *
 * La implementación la **genera el processor** (opción `kvalid.componentModel`) y solo delega:
 *
 * ```
 * @Component
 * public class AccountKvalidValidator : KvalidValidator<Account> {
 *     override val type: Class<Account> = Account::class.java
 *     override fun validate(value: Account): ValidationResult<Account> = value.validate()
 * }
 * ```
 *
 * La validación real sigue siendo **código generado sin reflexión**; lo único dinámico es
 * buscar el adaptador en un `Map<Class<*>, KvalidValidator<*>>` — una búsqueda en un mapa,
 * no reflexión.
 *
 * **Solo JVM**: usa [Class]. El `validate()` puro sigue disponible en todos los targets KMP.
 */
public interface KvalidValidator<T : Any> {
    /** El tipo que este adaptador valida. Clave de registro. */
    public val type: Class<T>

    /** Delega en el `validate()` generado para [T]. */
    public fun validate(value: T): ValidationResult<T>
}

/**
 * Registro por `ServiceLoader` para consumidores **sin contenedor DI** (JVM plano, Ktor).
 * Requiere generar los adaptadores con `kvalid.componentModel=serviceloader`, que además
 * escribe `META-INF/services/dev.kvalid.runtime.spi.KvalidValidator`.
 *
 * En Spring **no se usa esto**: el contenedor inyecta `List<KvalidValidator<*>>` directamente
 * (y Spring AOT lo registra para native-image, que `ServiceLoader` no da gratis).
 */
public object KvalidValidators {

    private val byType: Map<Class<*>, KvalidValidator<*>> by lazy {
        ServiceLoader.load(KvalidValidator::class.java).associateBy { it.type }
    }

    /** El adaptador de [type], o `null` si ese tipo no es `@Validated` (o no se generó). */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> forType(type: Class<T>): KvalidValidator<T>? =
        byType[type] as KvalidValidator<T>?

    /** Valida [value] si hay adaptador; si no lo hay, lo considera válido (nada que validar). */
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> validate(value: T): ValidationResult<T> =
        forType(value.javaClass as Class<T>)?.validate(value) ?: ValidationResult.Valid(value)
}
