package dev.kvalid.spring

import dev.kvalid.runtime.ValidationResult
import dev.kvalid.runtime.Violation
import dev.kvalid.runtime.spi.KvalidValidator
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KvalidSpringValidatorTest {

    data class Account(val name: String, val age: Int, val tags: List<String> = emptyList())

    /** Sustituye al adaptador que genera el processor (`kvalid.componentModel=spring`). */
    private class FakeAdapter(private val violations: List<Violation>) : KvalidValidator<Account> {
        override val type: Class<Account> = Account::class.java
        override fun validate(value: Account): ValidationResult<Account> =
            if (violations.isEmpty()) ValidationResult.Valid(value) else ValidationResult.Invalid(violations)
    }

    private fun validatorFor(vararg violations: Violation): KvalidSpringValidator =
        KvalidSpringValidator(KvalidValidatorRegistry(listOf(FakeAdapter(violations.toList()))))

    private fun errorsFor(account: Account) = BeanPropertyBindingResult(account, "account")

    private val account = Account("", 15)

    @Test
    fun `supports solo el tipo registrado`() {
        val validator = validatorFor()
        assertTrue(validator.supports(Account::class.java))
        assertFalse(validator.supports(String::class.java))
    }

    @Test
    fun `una violacion de campo se traduce a fieldError con code y args`() {
        val validator = validatorFor(Violation("age", "min", mapOf("min" to 18), "debe ser mayor de edad"))
        val errors = errorsFor(account)

        validator.validate(account, errors)

        val fieldError = errors.getFieldError("age")
        assertNotNull(fieldError)
        assertEquals("min", fieldError.code)
        assertEquals(listOf<Any?>(18), fieldError.arguments?.toList())
        assertEquals("debe ser mayor de edad", fieldError.defaultMessage)
        assertEquals(15, fieldError.rejectedValue)
    }

    @Test
    fun `acumula todas las violaciones - no corta en la primera`() {
        val validator = validatorFor(
            Violation("name", "notBlank"),
            Violation("age", "min", mapOf("min" to 18)),
        )
        val errors = errorsFor(account)

        validator.validate(account, errors)

        assertEquals(2, errors.errorCount)
        assertEquals(setOf("name", "age"), errors.fieldErrors.map { it.field }.toSet())
    }

    @Test
    fun `violacion sin path (cross-field) va a error global`() {
        val validator = validatorFor(Violation("", "passwordsMatch"))
        val errors = errorsFor(account)

        validator.validate(account, errors)

        assertEquals(0, errors.fieldErrorCount)
        assertEquals(1, errors.globalErrorCount)
        assertEquals("passwordsMatch", errors.globalError?.code)
    }

    /** Riesgo R3: un validador custom puede inventar un path que no es propiedad del target. */
    @Test
    fun `un path NO resoluble degrada a error global en vez de reventar`() {
        val validator = validatorFor(Violation("noSoyUnaPropiedad", "custom"))
        val errors = errorsFor(account)

        validator.validate(account, errors) // no debe lanzar

        assertEquals(1, errors.errorCount)
        assertEquals(1, errors.globalErrorCount)
        assertEquals("custom", errors.globalError?.code)
    }

    @Test
    fun `path indexado de coleccion se mapea como fieldError`() {
        val withTags = Account("ok", 30, listOf("a", " "))
        val validator = validatorFor(Violation("tags[1]", "notBlank"))
        val errors = errorsFor(withTags)

        validator.validate(withTags, errors)

        assertEquals("tags[1]", errors.fieldErrors.single().field)
    }

    @Test
    fun `sin violaciones no toca el BindingResult`() {
        val errors = errorsFor(account)
        validatorFor().validate(account, errors)
        assertFalse(errors.hasErrors())
    }

    @Test
    fun `los hints de @Validated se ignoran pero igual valida`() {
        val validator = validatorFor(Violation("name", "notBlank"))
        val errors = errorsFor(account)

        validator.validate(account, errors, "AlgunGrupo")

        assertEquals(1, errors.errorCount)
    }

    /**
     * Regresión: en kvalid `message` es opcional (el contrato es code + params). Si se propaga
     * como `defaultMessage` nulo, Spring falla al resolver el error construyendo el
     * ProblemDetail y el cuerpo del 400 se va **vacío**. Debe caer al `code`.
     */
    @Test
    fun `defaultMessage nunca es nulo - cae al code cuando la violacion no trae mensaje`() {
        val validator = validatorFor(
            Violation("name", "notBlank"),           // sin message
            Violation("", "passwordsMatch"),          // global, sin message
            Violation("noExiste", "custom"),          // fallback por path no resoluble
        )
        val errors = errorsFor(account)

        validator.validate(account, errors)

        errors.allErrors.forEach { error ->
            assertNotNull(error.defaultMessage, "defaultMessage nulo en ${error.code}")
        }
        assertEquals("notBlank", errors.getFieldError("name")?.defaultMessage)
    }

    @Test
    fun `si la violacion trae message, ese gana sobre el code`() {
        val validator = validatorFor(Violation("name", "notBlank", emptyMap(), "el nombre es obligatorio"))
        val errors = errorsFor(account)

        validator.validate(account, errors)

        assertEquals("el nombre es obligatorio", errors.getFieldError("name")?.defaultMessage)
    }

    @Test
    fun `composite - kvalid y otro validator acumulan en el mismo BindingResult`() {
        val otro = object : Validator {
            override fun supports(clazz: Class<*>) = clazz == Account::class.java
            override fun validate(target: Any, errors: Errors) = errors.reject("otroValidador")
        }
        val composite = CompositeValidator(listOf(validatorFor(Violation("name", "notBlank")), otro))
        val errors = errorsFor(account)

        assertTrue(composite.supports(Account::class.java))
        composite.validate(account, errors)

        assertEquals(2, errors.errorCount)
        assertEquals("notBlank", errors.getFieldError("name")?.code)
        assertEquals("otroValidador", errors.globalError?.code)
    }

    @Test
    fun `composite soporta un tipo si CUALQUIER delegado lo soporta`() {
        val soloStrings = object : Validator {
            override fun supports(clazz: Class<*>) = clazz == String::class.java
            override fun validate(target: Any, errors: Errors) = Unit
        }
        val composite = CompositeValidator(listOf(validatorFor(), soloStrings))

        assertTrue(composite.supports(String::class.java))
        assertTrue(composite.supports(Account::class.java))
        assertFalse(composite.supports(Int::class.java))
    }
}
