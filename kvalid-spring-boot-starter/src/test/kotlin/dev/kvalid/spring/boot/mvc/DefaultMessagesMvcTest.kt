package dev.kvalid.spring.boot.mvc

import dev.kvalid.i18n.MessageResolver
import dev.kvalid.runtime.getOrThrow
import dev.kvalid.spring.boot.CreateUser
import dev.kvalid.spring.boot.CreateUserKValidator
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(CreateUserKValidator::class, MessagesTestApp.UsersController::class)
open class MessagesTestApp {
    @RestController
    open class UsersController {
        @PostMapping("/users")
        open fun create(@Valid @RequestBody req: CreateUser): Map<String, String> = mapOf("status" to "ok")
    }
}

private const val INVALID = """{"name":"","email":"nope","age":15}"""

private fun MockMvc.messagesFor(body: String): Map<String, String?> {
    val result = perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest)
        .andReturn()
    val ex = result.resolvedException as MethodArgumentNotValidException
    return ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
}

/** Por defecto (`kvalid.messages=auto`) con locale inglés: texto legible, no el `code`. */
@SpringBootTest(classes = [MessagesTestApp::class], properties = ["kvalid.messages=en"])
@AutoConfigureMockMvc
class DefaultMessagesMvcTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `el 400 lleva mensajes legibles en vez del code`() {
        val mensajes = mockMvc.messagesFor(INVALID)
        assertEquals("must not be blank", mensajes["name"])
        assertEquals("must be a well-formed email address", mensajes["email"])
        // Y con el parámetro de la anotación interpolado.
        assertEquals("must be greater than or equal to 18", mensajes["age"])
    }
}

/** `kvalid.messages=es` cambia el idioma sin tocar código. */
@SpringBootTest(classes = [MessagesTestApp::class], properties = ["kvalid.messages=es"])
@AutoConfigureMockMvc
class SpanishMessagesMvcTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `los mensajes salen en espanol`() {
        val mensajes = mockMvc.messagesFor(INVALID)
        assertEquals("no puede estar en blanco", mensajes["name"])
        assertEquals("debe ser mayor o igual que 18", mensajes["age"])
    }
}

/** `kvalid.messages=none` conserva el comportamiento anterior a 0.4.0. */
@SpringBootTest(classes = [MessagesTestApp::class], properties = ["kvalid.messages=none"])
@AutoConfigureMockMvc
class NoMessagesMvcTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `con none el defaultMessage vuelve a ser el code`() {
        val mensajes = mockMvc.messagesFor(INVALID)
        assertEquals("notBlank", mensajes["name"])
        assertEquals("email", mensajes["email"])
    }
}

/** Un `MessageResolver` propio gana sobre la propiedad. */
@SpringBootTest(classes = [CustomResolverMvcTest.App::class], properties = ["kvalid.messages=en"])
@AutoConfigureMockMvc
class CustomResolverMvcTest(@Autowired val mockMvc: MockMvc) {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(CreateUserKValidator::class, MessagesTestApp.UsersController::class)
    open class App {
        @Bean
        open fun miResolver(): MessageResolver = MessageResolver { "[${it.code}] a mi manera" }
    }

    @Test
    fun `el bean del usuario sustituye a los mensajes por defecto`() {
        val mensajes = mockMvc.messagesFor(INVALID)
        assertEquals("[notBlank] a mi manera", mensajes["name"])
        assertTrue(mensajes["age"]!!.startsWith("[min]"))
    }
}

/**
 * El camino explícito (`validate().getOrThrow()` → `KValidExceptionHandler`) tiene que dar el
 * MISMO texto que `@Valid`. Antes daba `message: null` para las mismas violaciones.
 */
@SpringBootTest(classes = [ExplicitPathMvcTest.App::class], properties = ["kvalid.messages=en"])
@AutoConfigureMockMvc
class ExplicitPathMvcTest(@Autowired val mockMvc: MockMvc) {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(CreateUserKValidator::class, App.ExplicitController::class)
    open class App {
        @RestController
        open class ExplicitController {
            /** El camino explícito: nada de `@Valid`, el controller lanza él mismo. */
            @PostMapping("/explicit")
            open fun create(@RequestBody req: CreateUser): Map<String, String> {
                CreateUserKValidator().validate(req).getOrThrow()
                return mapOf("status" to "ok")
            }
        }
    }

    @Test
    fun `getOrThrow da el mismo mensaje que @Valid`() {
        val body = mockMvc.perform(
            post("/explicit").contentType(MediaType.APPLICATION_JSON).content(INVALID),
        ).andExpect(status().isBadRequest).andReturn().response.contentAsString

        assertTrue("must not be blank" in body, body)
        assertTrue("must be greater than or equal to 18" in body, body)
    }
}
