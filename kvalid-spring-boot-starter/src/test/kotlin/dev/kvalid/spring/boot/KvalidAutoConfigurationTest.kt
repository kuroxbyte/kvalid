package dev.kvalid.spring.boot

import dev.kvalid.spring.CompositeValidator
import dev.kvalid.spring.KvalidExceptionHandler
import dev.kvalid.spring.KvalidSpringValidator
import dev.kvalid.spring.KvalidValidatorRegistry
import dev.kvalid.spring.KvalidWebFluxConfigurer
import dev.kvalid.spring.KvalidWebMvcConfigurer
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.validation.Validator
import kotlin.test.Test

/**
 * La auto-configuración se prueba con `ApplicationContextRunner` — el estándar de Boot para
 * verificar que los beans aparecen/desaparecen según classpath, tipo de app y properties.
 */
class KvalidAutoConfigurationTest {

    private val autoConfig = AutoConfigurations.of(KvalidAutoConfiguration::class.java)

    @Test
    fun `en app NO web registra el registry, el validador y el advice`() {
        ApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KvalidValidatorRegistry::class.java)
                assertThat(context).hasSingleBean(KvalidSpringValidator::class.java)
                assertThat(context).hasSingleBean(KvalidExceptionHandler::class.java)
                // Sin stack web no se registra ningún configurer.
                assertThat(context).doesNotHaveBean(KvalidWebMvcConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KvalidWebFluxConfigurer::class.java)
                assertThat(context.getBean(KvalidValidatorRegistry::class.java).size).isEqualTo(1)
            }
    }

    @Test
    fun `kvalid enabled=false desactiva todo`() {
        ApplicationContextRunner().withConfiguration(autoConfig)
            .withPropertyValues("kvalid.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(KvalidValidatorRegistry::class.java)
                assertThat(context).doesNotHaveBean(KvalidSpringValidator::class.java)
            }
    }

    @Test
    fun `app servlet registra el WebMvcConfigurer y NO el de WebFlux`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KvalidWebMvcConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KvalidWebFluxConfigurer::class.java)
            }
    }

    @Test
    fun `app reactiva registra el WebFluxConfigurer y NO el de MVC`() {
        ReactiveWebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KvalidWebFluxConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KvalidWebMvcConfigurer::class.java)
            }
    }

    /** Riesgo R1: quien ya registra su propio validador global debe poder apagar el nuestro. */
    @Test
    fun `register-validator=false no registra configurer (evita el choque de getValidator)`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .withPropertyValues("kvalid.web.register-validator=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(KvalidWebMvcConfigurer::class.java)
                // el resto sigue disponible para uso explícito
                assertThat(context).hasSingleBean(KvalidSpringValidator::class.java)
            }
    }

    /** Riesgo R2: con Hibernate Validator presente, se COMPONEN (no se sustituye). */
    @Test
    fun `con Jakarta Bean Validation presente el validador global es un composite`() {
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ValidationAutoConfiguration::class.java,
                    KvalidAutoConfiguration::class.java,
                ),
            )
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                val global = context.getBean(
                    KvalidAutoConfiguration.KVALID_WEB_VALIDATOR,
                    CompositeValidator::class.java,
                )
                assertThat(global.delegates).hasSize(2)
            }
    }

    @Test
    fun `sin Jakarta Bean Validation el composite lleva solo a kvalid`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                val global = context.getBean(
                    KvalidAutoConfiguration.KVALID_WEB_VALIDATOR,
                    CompositeValidator::class.java,
                )
                assertThat(global.delegates).hasSize(1)
                assertThat(global.delegates.single()).isInstanceOf(KvalidSpringValidator::class.java)
            }
    }

    /** Un bean por tipo: si no, un `@Autowired KvalidSpringValidator` fallaría por ambigüedad. */
    @Test
    fun `no hay ambiguedad de beans por tipo`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKvalidValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KvalidSpringValidator::class.java)
                assertThat(context).hasSingleBean(CompositeValidator::class.java)
            }
    }
}
