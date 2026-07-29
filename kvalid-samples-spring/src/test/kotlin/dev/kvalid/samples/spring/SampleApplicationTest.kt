package dev.kvalid.samples.spring

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.Test

/**
 * Prueba que la app de ejemplo funciona de verdad, y —lo importante— que **KSP y APT conviven
 * en la misma aplicación**: el endpoint Kotlin y el Java validan por el mismo camino de Spring.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SampleApplicationTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `DTO Kotlin (KSP) invalido devuelve 400 con los campos`() {
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"","email":"nope","age":15}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("notBlank"))
            .andExpect(jsonPath("$.errors[?(@.field=='email')].code").value("email"))
            .andExpect(jsonPath("$.errors[?(@.field=='age')].code").value("min"))
    }

    @Test
    fun `DTO Java (APT) invalido devuelve 400 con los campos`() {
        mockMvc.perform(
            post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""{"reference":"","quantity":0}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errors[?(@.field=='reference')].code").value("notBlank"))
            .andExpect(jsonPath("$.errors[?(@.field=='quantity')].code").value("min"))
    }

    @Test
    fun `peticiones validas pasan al controlador en ambas variantes`() {
        mockMvc.perform(
            post("/users").contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Ana","email":"ana@example.com","age":30}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.created").value("Ana"))

        mockMvc.perform(
            post("/orders").contentType(MediaType.APPLICATION_JSON)
                .content("""{"reference":"ORD-1","quantity":3}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.reference").value("ORD-1"))
    }
}
