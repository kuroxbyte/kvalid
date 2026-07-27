package dev.kvalid.core

import dev.genkit.model.AnnotationModel
import dev.genkit.model.ClassModel
import dev.genkit.ports.testing.InMemoryTypeResolver
import dev.genkit.ports.testing.RecordingDiagnosticReporter
import dev.kvalid.core.build.ValidationModelBuilder
import dev.kvalid.core.model.Constraint
import dev.kvalid.core.model.ValidationDiagnostics
import dev.kvalid.core.model.ValidationModel
import dev.kvalid.core.model.ValidationNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationModelBuilderTest {

    private fun build(
        root: ClassModel,
        universe: List<ClassModel> = emptyList(),
        annotated: Set<Pair<String, String>> = emptySet(),
    ): Pair<ValidationModel, RecordingDiagnosticReporter> {
        val reporter = RecordingDiagnosticReporter()
        val resolver = InMemoryTypeResolver(universe + root, annotated)
        return ValidationModelBuilder(resolver, reporter).build(root) to reporter
    }

    @Test
    fun `constraints de texto y numericos se parsean con sus args`() {
        val (model, reporter) = build(
            validated(
                "fx.User",
                prop("name", STRING, ann(ValidationNames.NOT_BLANK), ann(ValidationNames.SIZE, "min" to 1, "max" to 80)),
                prop("age", INT, ann(ValidationNames.RANGE, "min" to 18L, "max" to 120L)),
                prop("email", STRING, ann(ValidationNames.EMAIL)),
            ),
        )
        assertFalse(reporter.hasErrors())
        val name = model.fields.single { it.name == "name" }.constraints
        assertTrue(name.any { it is Constraint.NotBlank })
        assertEquals(Constraint.Size(1, 80), name.filterIsInstance<Constraint.Size>().single())
        assertEquals(Constraint.Range(18, 120), model.fields.single { it.name == "age" }.constraints.single())
    }

    @Test
    fun `constraint sobre tipo incompatible es error`() {
        val (_, reporter) = build(
            validated("fx.User", prop("age", STRING, ann(ValidationNames.RANGE, "min" to 0L, "max" to 9L))),
        )
        assertTrue(ValidationDiagnostics.CONSTRAINT_TYPE in reporter.codes)
    }

    @Test
    fun `Size aplica a String y a coleccion`() {
        val (model, reporter) = build(
            validated(
                "fx.User",
                prop("name", STRING, ann(ValidationNames.SIZE, "max" to 5)),
                prop("tags", listType("kotlin.String"), ann(ValidationNames.SIZE, "min" to 1)),
            ),
        )
        assertFalse(reporter.hasErrors())
        assertTrue(model.fields.all { it.constraints.any { c -> c is Constraint.Size } })
    }

    @Test
    fun `cascada cuando el tipo de la propiedad es Validated`() {
        val (model, _) = build(
            validated("fx.Order", prop("address", type("fx.Address"))),
            annotated = setOf("fx.Address" to ValidationNames.VALIDATED),
        )
        assertTrue(model.fields.single { it.name == "address" }.cascade)
    }

    @Test
    fun `constraint custom (meta-anotada) se resuelve a su validador`() {
        val slugDecl = constraintAnnotationDecl("fx.Slug", "fx.SlugValidator")
        val (model, reporter) = build(
            validated("fx.User", prop("handle", STRING, AnnotationModel("fx.Slug"))),
            universe = listOf(slugDecl),
        )
        val custom = model.fields.single { it.name == "handle" }.constraints
            .filterIsInstance<Constraint.Custom>().single()
        assertEquals("fx.SlugValidator", custom.validatorFqn)
        assertFalse(reporter.hasErrors())
    }

    @Test
    fun `anotacion de clase meta-anotada produce un validador cross-field`() {
        val ruleDecl = constraintAnnotationDecl("fx.DateOk", "fx.DateOkValidator")
        val (model, _) = build(
            validated("fx.Range", prop("start", INT), prop("end", INT), classAnnotations = listOf(AnnotationModel("fx.DateOk"))),
            universe = listOf(ruleDecl),
        )
        assertEquals(listOf("fx.DateOkValidator"), model.classValidators.map { it.validatorFqn })
    }

    @Test
    fun `sin custom no hay validadores de clase`() {
        val (model, _) = build(validated("fx.User", prop("name", STRING)))
        assertTrue(model.classValidators.isEmpty())
        assertTrue(model.fields.single().constraints.none { it is Constraint.Custom })
    }

    @Test
    fun `bounds invalidos son error en build-time`() {
        val (_, r1) = build(validated("fx.A", prop("name", STRING, ann(ValidationNames.SIZE, "min" to 5, "max" to 1))))
        assertTrue(ValidationDiagnostics.CONSTRAINT_ARGS in r1.codes)

        val (_, r2) = build(validated("fx.A", prop("age", INT, ann(ValidationNames.RANGE, "min" to 9L, "max" to 1L))))
        assertTrue(ValidationDiagnostics.CONSTRAINT_ARGS in r2.codes)

        val (_, r3) = build(validated("fx.A", prop("code", STRING, ann(ValidationNames.PATTERN, "regex" to "[unclosed"))))
        assertTrue(ValidationDiagnostics.CONSTRAINT_ARGS in r3.codes)

        val (_, r4) = build(validated("fx.A", prop("price", type("java.math.BigDecimal"), ann(ValidationNames.DECIMAL_MIN, "value" to "abc"))))
        assertTrue(ValidationDiagnostics.CONSTRAINT_ARGS in r4.codes)
    }

    @Test
    fun `anotacion compuesta expande sus constraints`() {
        val username = compositeAnnotationDecl(
            "fx.Username",
            AnnotationModel(ValidationNames.NOT_BLANK),
            ann(ValidationNames.SIZE, "min" to 3, "max" to 20),
        )
        val (model, reporter) = build(
            validated("fx.User", prop("handle", STRING, AnnotationModel("fx.Username"))),
            universe = listOf(username),
        )
        val cs = model.fields.single().constraints
        assertTrue(cs.any { it is Constraint.NotBlank })
        assertEquals(Constraint.Size(3, 20), cs.filterIsInstance<Constraint.Size>().single())
        assertFalse(reporter.hasErrors())
    }

    @Test
    fun `OneOf y Url se parsean`() {
        val oneOf = AnnotationModel(
            ValidationNames.ONE_OF,
            mapOf("values" to dev.genkit.model.AnnotationArg.ArrayOf(
                listOf(dev.genkit.model.AnnotationArg.Primitive("A"), dev.genkit.model.AnnotationArg.Primitive("B")),
            )),
        )
        val (model, _) = build(
            validated(
                "fx.A",
                prop("kind", STRING, oneOf),
                prop("site", STRING, AnnotationModel(ValidationNames.URL)),
            ),
        )
        assertEquals(
            Constraint.OneOf(listOf("A", "B")),
            model.fields.single { it.name == "kind" }.constraints.single(),
        )
        assertTrue(model.fields.single { it.name == "site" }.constraints.single() is Constraint.Url)
    }

    @Test
    fun `constraints de elemento se leen del argumento de tipo`() {
        val elementType = STRING.copy(annotations = listOf(AnnotationModel(ValidationNames.NOT_BLANK)))
        val listOfTags = type("kotlin.collections.List", kind = dev.genkit.model.TypeKind.COLLECTION_LIST, args = listOf(elementType))
        val (model, reporter) = build(validated("fx.Post", prop("tags", listOfTags)))
        val field = model.fields.single { it.name == "tags" }
        assertTrue(field.elementConstraints.any { it is Constraint.NotBlank })
        assertFalse(reporter.hasErrors())
    }

    @Test
    fun `message opcional se propaga al constraint`() {
        val (model, _) = build(
            validated("fx.User", prop("name", STRING, ann(ValidationNames.NOT_BLANK, "message" to "requerido"))),
        )
        val nb = model.fields.single().constraints.filterIsInstance<Constraint.NotBlank>().single()
        assertEquals("requerido", nb.message)
    }
}
