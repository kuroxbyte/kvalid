package dev.kvalid.processor

import dev.genkit.model.AnnotationModel
import dev.genkit.model.ClassModel
import dev.genkit.model.PropertyModel
import dev.genkit.model.TypeKind
import dev.genkit.model.TypeRef
import dev.genkit.ports.testing.InMemoryTypeResolver
import dev.genkit.ports.testing.RecordingDiagnosticReporter
import dev.kvalid.core.build.ValidationModelBuilder
import dev.kvalid.core.model.ValidationNames
import kotlin.test.Test
import kotlin.test.assertFalse

/** El validador generado con KotlinPoet no usa reflexión (base de la compatibilidad native-image). */
class ZeroReflectionTest {

    private val reflectionTokens = listOf(
        "::class", "kotlin.reflect", "java.lang.reflect", ".javaClass",
        "Class.forName", "getDeclaredField", "isAccessible", "kotlin.jvm.internal.Reflection",
    )

    private fun t(qn: String) = TypeRef(qn, packageName = qn.substringBeforeLast('.', ""))

    @Test
    fun `el validador generado no contiene ninguna llamada reflexiva`() {
        val root = ClassModel(
            type = TypeRef("fx.User", kind = TypeKind.DATA_CLASS, packageName = "fx"),
            properties = listOf(
                PropertyModel("name", t("kotlin.String"), annotations = listOf(
                    AnnotationModel(ValidationNames.NOT_BLANK),
                    AnnotationModel(ValidationNames.SIZE, mapOf("max" to dev.genkit.model.AnnotationArg.Primitive(80))),
                )),
                PropertyModel("age", t("kotlin.Int"), annotations = listOf(
                    AnnotationModel(ValidationNames.RANGE, mapOf(
                        "min" to dev.genkit.model.AnnotationArg.Primitive(18L),
                        "max" to dev.genkit.model.AnnotationArg.Primitive(120L),
                    )),
                )),
                PropertyModel("email", t("kotlin.String"), annotations = listOf(AnnotationModel(ValidationNames.EMAIL))),
            ),
            annotations = listOf(AnnotationModel(ValidationNames.VALIDATED)),
        )
        val resolver = InMemoryTypeResolver(listOf(root))
        val model = ValidationModelBuilder(resolver, RecordingDiagnosticReporter()).build(root)
        val code = ValidationEmitter().emit(model).content

        reflectionTokens.forEach { token ->
            assertFalse(token in code, "El generado no debe usar reflexión, pero contiene '$token':\n$code")
        }
    }
}
