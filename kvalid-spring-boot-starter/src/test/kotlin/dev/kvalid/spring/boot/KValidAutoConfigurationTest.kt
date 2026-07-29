package dev.kvalid.spring.boot

import dev.kvalid.spring.CompositeValidator
import dev.kvalid.spring.KValidExceptionHandler
import dev.kvalid.spring.KValidSpringValidator
import dev.kvalid.spring.KValidatorRegistry
import dev.kvalid.spring.KValidWebFluxConfigurer
import dev.kvalid.spring.KValidWebMvcConfigurer
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
class KValidAutoConfigurationTest {

    private val autoConfig = AutoConfigurations.of(KValidAutoConfiguration::class.java)

    @Test
    fun `en app NO web registra el registry, el validador y el advice`() {
        ApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KValidatorRegistry::class.java)
                assertThat(context).hasSingleBean(KValidSpringValidator::class.java)
                assertThat(context).hasSingleBean(KValidExceptionHandler::class.java)
                // Sin stack web no se registra ningún configurer.
                assertThat(context).doesNotHaveBean(KValidWebMvcConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KValidWebFluxConfigurer::class.java)
                assertThat(context.getBean(KValidatorRegistry::class.java).size).isEqualTo(1)
            }
    }

    @Test
    fun `kvalid enabled=false desactiva todo`() {
        ApplicationContextRunner().withConfiguration(autoConfig)
            .withPropertyValues("kvalid.enabled=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(KValidatorRegistry::class.java)
                assertThat(context).doesNotHaveBean(KValidSpringValidator::class.java)
            }
    }

    @Test
    fun `app servlet registra el WebMvcConfigurer y NO el de WebFlux`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KValidWebMvcConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KValidWebFluxConfigurer::class.java)
            }
    }

    @Test
    fun `app reactiva registra el WebFluxConfigurer y NO el de MVC`() {
        ReactiveWebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KValidWebFluxConfigurer::class.java)
                assertThat(context).doesNotHaveBean(KValidWebMvcConfigurer::class.java)
            }
    }

    /** Riesgo R1: quien ya registra su propio validador global debe poder apagar el nuestro. */
    @Test
    fun `register-validator=false no registra configurer (evita el choque de getValidator)`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .withPropertyValues("kvalid.web.register-validator=false")
            .run { context ->
                assertThat(context).doesNotHaveBean(KValidWebMvcConfigurer::class.java)
                // el resto sigue disponible para uso explícito
                assertThat(context).hasSingleBean(KValidSpringValidator::class.java)
            }
    }

    /** Riesgo R2: con Hibernate Validator presente, se COMPONEN (no se sustituye). */
    @Test
    fun `con Jakarta Bean Validation presente el validador global es un composite`() {
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ValidationAutoConfiguration::class.java,
                    KValidAutoConfiguration::class.java,
                ),
            )
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                val global = context.getBean(
                    KValidAutoConfiguration.KVALID_WEB_VALIDATOR,
                    CompositeValidator::class.java,
                )
                assertThat(global.delegates).hasSize(2)
            }
    }

    @Test
    fun `sin Jakarta Bean Validation el composite lleva solo a kvalid`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                val global = context.getBean(
                    KValidAutoConfiguration.KVALID_WEB_VALIDATOR,
                    CompositeValidator::class.java,
                )
                assertThat(global.delegates).hasSize(1)
                assertThat(global.delegates.single()).isInstanceOf(KValidSpringValidator::class.java)
            }
    }

    /** Un bean por tipo: si no, un `@Autowired KValidSpringValidator` fallaría por ambigüedad. */
    @Test
    fun `no hay ambiguedad de beans por tipo`() {
        WebApplicationContextRunner().withConfiguration(autoConfig)
            .withUserConfiguration(CreateUserKValidator::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(KValidSpringValidator::class.java)
                assertThat(context).hasSingleBean(CompositeValidator::class.java)
            }
    }
}
