package dev.kvalid.spring.boot

import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuración del starter (prefijo `kvalid`). */
@ConfigurationProperties(prefix = "kvalid")
public data class KvalidProperties(
    /** Desactiva por completo la integración (`kvalid.enabled=false`). */
    val enabled: Boolean = true,
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
