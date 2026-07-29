package dev.kvalid.spring.boot.mvc

import dev.kvalid.spring.boot.CreateUser
import dev.kvalid.spring.boot.CreateUserKvalidValidator
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sin `@ComponentScan`: se importa solo lo necesario para no arrastrar la app reactiva. */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CreateUserKvalidValidator::class, MvcTestApp.UsersController::class)
open class MvcTestApp {

    @RestController
    open class UsersController {
        /** Sin `validate().getOrThrow()`: lo dispara `@Valid` a través del Validator SPI. */
        @PostMapping("/users")
        open fun create(@Valid @RequestBody req: CreateUser): Map<String, String> = mapOf("status" to "ok")
    }
}

/**
 * Prueba de fuego en **servlet**: `@Valid @RequestBody` sobre un tipo de kvalid da un 400 con
 * los errores por campo, **sin que el controller llame a `validate()`**.
 */
@SpringBootTest(classes = [MvcTestApp::class], properties = ["spring.mvc.problemdetails.enabled=true"])
@AutoConfigureMockMvc
class KvalidMvcIntegrationTest(@Autowired val mockMvc: MockMvc) {

    private val invalidBody = """{"name":"","email":"nope","age":15}"""

    @Test
    fun `request invalido devuelve 400 y las violaciones de kvalid llegan al BindingResult`() {
        val result = mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON).content(invalidBody),
        ).andExpect(status().isBadRequest).andReturn()

        // Que Spring lance MethodArgumentNotValidException prueba que las violaciones entraron
        // por el camino NATIVO de @Valid (Validator SPI → BindingResult), no por un hook nuestro.
        val ex = result.resolvedException as MethodArgumentNotValidException
        val byField = ex.bindingResult.fieldErrors.associate { it.field to it.code }
        assertEquals(mapOf("name" to "notBlank", "email" to "email", "age" to "min"), byField)

        // Los params viajan como args posicionales para el MessageSource (i18n).
        assertEquals(listOf<Any?>(18), ex.bindingResult.getFieldError("age")?.arguments?.toList())
    }

    /**
     * Regresión: con `defaultMessage` nulo, Spring no puede resolver el error al construir el
     * ProblemDetail y el cuerpo del 400 se iba **vacío**. Debe traer siempre cuerpo.
     */
    @Test
    fun `el 400 se serializa como ProblemDetail con cuerpo`() {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(invalidBody))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Bad Request"))
    }

    @Test
    fun `request valido pasa al controller`() {
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Ana","email":"ana@x.com","age":30}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }
}
