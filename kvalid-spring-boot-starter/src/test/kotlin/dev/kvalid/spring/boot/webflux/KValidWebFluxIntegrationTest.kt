package dev.kvalid.spring.boot.webflux

import dev.kvalid.spring.boot.CreateUser
import dev.kvalid.spring.boot.CreateUserKValidator
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CreateUserKValidator::class, WebFluxTestApp.UsersController::class)
open class WebFluxTestApp {

    @RestController
    open class UsersController {
        @PostMapping("/users")
        open fun create(@Valid @RequestBody req: CreateUser): Map<String, String> = mapOf("status" to "ok")
    }
}

/**
 * El mismo `KValidSpringValidator` sirve a **WebFlux** sin cambios: lo único distinto es la
 * interfaz de configuración (`WebFluxConfigurer` en vez de `WebMvcConfigurer`). Aquí se
 * verifica que `@Valid` reactivo también produce el 400 con los campos.
 */
@SpringBootTest(
    classes = [WebFluxTestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.main.web-application-type=reactive",
        "spring.webflux.problemdetails.enabled=true",
    ],
)
class KValidWebFluxIntegrationTest(@Autowired val client: WebTestClient) {

    @Test
    fun `request invalido devuelve 400 con cuerpo ProblemDetail`() {
        client.post().uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"","email":"nope","age":15}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.status").isEqualTo(400)
            .jsonPath("$.title").isEqualTo("Bad Request")
    }

    @Test
    fun `request valido pasa al controller`() {
        client.post().uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"name":"Ana","email":"ana@x.com","age":30}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("ok")
    }
}
