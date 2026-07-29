package dev.kvalid.spring.boot

import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.Test

/**
 * Las properties deben ENLAZARSE de verdad, no solo existir: el `@ConditionalOnProperty` lee
 * el Environment directamente, así que un fallo de binding pasaría desapercibido en los demás
 * tests. Requiere `kotlin-reflect` en el classpath (lo declara el starter).
 */
class KValidPropertiesBindingTest {
    @Test
    fun `las properties se enlazan de verdad`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KValidAutoConfiguration::class.java))
            .withPropertyValues("kvalid.web.register-validator=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                val props = context.getBean(KValidProperties::class.java)
                assertThat(props.web.registerValidator).isFalse()
            }
    }
}
