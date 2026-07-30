package dev.kvalid.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuración del starter (prefijo `kvalid`). */
@ConfigurationProperties(prefix = "kvalid")
public data class KValidProperties(
    /** Desactiva por completo la integración (`kvalid.enabled=false`). */
    val enabled: Boolean = true,

    /**
     * Idioma de los mensajes por defecto de las violaciones sin `message` propio:
     * `auto` (el locale de la JVM), `en`, `es`, o `none` para quedarse con el `code` crudo.
     *
     * Solo afecta al `defaultMessage`: si defines un `MessageSource` con entradas para el
     * `code`, Spring sigue usando el tuyo. Y un bean `MessageResolver` propio gana sobre esto.
     */
    val messages: String = "auto",

    val web: Web = Web(),
) {
    public data class Web(
        /**
         * Registrar el validador de kvalid como el global de MVC/WebFlux — lo que hace que
         * `@Valid` funcione sin escribir nada.
         *
         * Ponlo en `false` si ya registras tu propio validador global: Spring **falla** si
         * dos `WebMvcConfigurer` devuelven un validador no nulo.
         */
        val registerValidator: Boolean = true,
    )
}
