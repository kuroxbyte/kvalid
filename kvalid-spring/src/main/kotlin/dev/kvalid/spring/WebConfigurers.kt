package dev.kvalid.spring

import org.springframework.validation.Validator
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Registra [validator] como el validador global de **Spring MVC** (servlet), que es el que
 * usa `@Valid @RequestBody`.
 *
 * ⚠️ `WebMvcConfigurerComposite` **falla si más de un `WebMvcConfigurer` devuelve un validador
 * no nulo**. Por eso la auto-configuración lo declara `@ConditionalOnMissingBean` y se puede
 * desactivar con `kvalid.web.register-validator=false` para quien ya registre el suyo.
 */
public class KvalidWebMvcConfigurer(
    private val validator: Validator,
) : WebMvcConfigurer {
    override fun getValidator(): Validator = validator
}

/**
 * Equivalente para **WebFlux** (reactivo). El mismo [KvalidSpringValidator] sirve a los dos
 * stacks: lo que cambia es solo la interfaz de configuración.
 */
public class KvalidWebFluxConfigurer(
    private val validator: Validator,
) : WebFluxConfigurer {
    override fun getValidator(): Validator = validator
}
