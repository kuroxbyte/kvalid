package dev.kvalid.spring.boot

import dev.kvalid.runtime.spi.KValidator
import dev.kvalid.spring.CompositeValidator
import dev.kvalid.spring.KValidExceptionHandler
import dev.kvalid.spring.KValidSpringValidator
import dev.kvalid.spring.KValidatorRegistry
import dev.kvalid.spring.KValidWebFluxConfigurer
import dev.kvalid.spring.KValidWebMvcConfigurer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.Validator
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Auto-configuración de kvalid.
 *
 * Recolecta los adaptadores generados (`@Component`, opción de KSP
 * `kvalid.componentModel=spring`), los indexa y registra el puente con el `Validator` SPI de
 * Spring — con lo que `@Valid @RequestBody` valida **nativamente en MVC y en WebFlux**.
 *
 * Todo es `@ConditionalOnMissingBean`: cualquier bean propio del usuario gana.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "kvalid", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KValidProperties::class)
public open class KValidAutoConfiguration {

    /** Índice de los adaptadores. Si no hay ninguno, queda vacío (no rompe el arranque). */
    @Bean
    @ConditionalOnMissingBean
    public open fun kvalidValidatorRegistry(
        validators: ObjectProvider<KValidator<*>>,
    ): KValidatorRegistry = KValidatorRegistry(validators.orderedStream().toList())

    @Bean
    @ConditionalOnMissingBean
    public open fun kvalidSpringValidator(registry: KValidatorRegistry): KValidSpringValidator =
        KValidSpringValidator(registry)

    /** `ValidationException` (estilo explícito `validate().getOrThrow()`) → 400. */
    @Bean
    @ConditionalOnMissingBean
    public open fun kvalidExceptionHandler(): KValidExceptionHandler = KValidExceptionHandler()

    /**
     * El validador que se registra como global. Si Boot ya expone `defaultValidator`
     * (Hibernate Validator, vía spring-boot-starter-validation), se **componen** en vez de
     * sustituirlo: si no, las anotaciones Jakarta del usuario dejarían de aplicarse.
     */
    @Bean(KVALID_WEB_VALIDATOR)
    @ConditionalOnMissingBean(name = [KVALID_WEB_VALIDATOR])
    public open fun kvalidWebValidator(
        kvalid: KValidSpringValidator,
        @Qualifier("defaultValidator") defaultValidator: ObjectProvider<Validator>,
    ): CompositeValidator {
        // SIEMPRE se envuelve, aunque solo haya un delegado: si devolviera el propio
        // KValidSpringValidator, habría dos beans del mismo tipo (kvalidSpringValidator y
        // kvalidWebValidator) y un `@Autowired KValidSpringValidator` del usuario fallaría
        // por ambigüedad. Envolver mantiene un bean por tipo y el comportamiento uniforme.
        val jakarta = defaultValidator.getIfAvailable()
        return CompositeValidator(listOfNotNull(kvalid, jakarta))
    }

    /** Servlet (Spring MVC). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer::class)
    @ConditionalOnProperty(
        prefix = "kvalid.web",
        name = ["register-validator"],
        havingValue = "true",
        matchIfMissing = true,
    )
    public open class Mvc {
        @Bean
        @ConditionalOnMissingBean
        public open fun kvalidWebMvcConfigurer(
            @Qualifier(KVALID_WEB_VALIDATOR) validator: Validator,
        ): KValidWebMvcConfigurer = KValidWebMvcConfigurer(validator)
    }

    /** Reactivo (WebFlux). Mismo validador, distinta interfaz de configuración. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(WebFluxConfigurer::class)
    @ConditionalOnProperty(
        prefix = "kvalid.web",
        name = ["register-validator"],
        havingValue = "true",
        matchIfMissing = true,
    )
    public open class WebFlux {
        @Bean
        @ConditionalOnMissingBean
        public open fun kvalidWebFluxConfigurer(
            @Qualifier(KVALID_WEB_VALIDATOR) validator: Validator,
        ): KValidWebFluxConfigurer = KValidWebFluxConfigurer(validator)
    }

    public companion object {
        /** Nombre del bean del validador global de kvalid (para `@Qualifier` y overrides). */
        public const val KVALID_WEB_VALIDATOR: String = "kvalidWebValidator"
    }
}
