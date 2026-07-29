package dev.kvalid.samples.spring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * App de ejemplo: **KValid con `@Valid` nativo de Spring**.
 *
 * ```
 * ./gradlew :kvalid-samples-spring:run
 *
 * # Kotlin (KSP) — inválido
 * curl -s -XPOST localhost:8080/users -H 'Content-Type: application/json' \
 *      -d '{"name":"","email":"nope","age":15}'
 *
 * # Java (APT) — inválido
 * curl -s -XPOST localhost:8080/orders -H 'Content-Type: application/json' \
 *      -d '{"reference":"","quantity":0}'
 * ```
 *
 * Ningún controlador llama a `validate()`: la validación la dispara `@Valid`, porque el
 * starter registra el puente de KValid con el `Validator` SPI de Spring.
 */
@SpringBootApplication
class SampleApplication

fun main(args: Array<String>) {
    runApplication<SampleApplication>(*args)
}
