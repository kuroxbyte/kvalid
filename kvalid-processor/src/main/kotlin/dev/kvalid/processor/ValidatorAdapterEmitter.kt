package dev.kvalid.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import dev.genkit.emit.toGeneratedFile
import dev.genkit.model.TypeRef
import dev.genkit.ports.GeneratedFile

/**
 * Cómo se descubre el adaptador generado en runtime. Opción de KSP `kvalid.componentModel`.
 *
 * El adaptador (`KvalidValidator<T>`) existe porque el `validate()` generado es una *extension
 * function*, que no se puede despachar genéricamente desde un borde que recibe `Any` (el
 * `Validator` de Spring, un filtro...). Ver `dev.kvalid.runtime.spi.KvalidValidator`.
 */
internal enum class ComponentModel {
    /** No se genera adaptador (default): solo la extension `validate()`. */
    NONE,

    /** Adaptador anotado `@Component`: lo recolecta Spring (y Spring AOT para native-image). */
    SPRING,

    /** Adaptador + `META-INF/services`: para JVM sin contenedor DI (Ktor, JVM plano). */
    SERVICE_LOADER,

    ;

    internal companion object {
        const val OPTION: String = "kvalid.componentModel"

        /** `null`/desconocido → [NONE]. El processor avisa por warning si el valor no se reconoce. */
        fun parse(raw: String?): ComponentModel? = when (raw?.trim()?.lowercase()) {
            null, "", "none" -> NONE
            "spring" -> SPRING
            "serviceloader", "service_loader" -> SERVICE_LOADER
            else -> null
        }
    }
}

/**
 * Emite el adaptador tipado que delega en la extension generada:
 *
 * ```
 * @Component                                          // solo con componentModel = spring
 * public class AccountKvalidValidator : KvalidValidator<Account> {
 *     override val type: Class<Account> = Account::class.java
 *     override fun validate(value: Account): ValidationResult<Account> = value.validate()
 * }
 * ```
 *
 * **Solo se invoca en target JVM**: `Class<T>` y `@Component` no existen en JS/Native, así que
 * emitirlo en `commonMain` rompería la build KMP del consumidor.
 */
internal class ValidatorAdapterEmitter {

    private companion object {
        val RESULT = ClassName("dev.kvalid.runtime", "ValidationResult")
        val SPI = ClassName("dev.kvalid.runtime.spi", "KvalidValidator")
        val JAVA_CLASS = ClassName("java.lang", "Class")
        val SPRING_COMPONENT = ClassName("org.springframework.stereotype", "Component")
    }

    /** Nombre del adaptador de [type]. Aplana los anidados (`Outer.Inner` → `OuterInner…`). */
    fun adapterName(type: TypeRef): String = simpleNamesOf(type).joinToString("") + "KvalidValidator"

    /** FQN del adaptador, para la línea de `META-INF/services`. */
    fun adapterFqn(type: TypeRef): String {
        val pkg = packageOf(type)
        val name = adapterName(type)
        return if (pkg.isEmpty()) name else "$pkg.$name"
    }

    fun emit(type: TypeRef, componentModel: ComponentModel): GeneratedFile {
        val pkg = packageOf(type)
        val self = ClassName(pkg, simpleNamesOf(type))
        val name = adapterName(type)

        val typeProp = PropertySpec
            .builder("type", JAVA_CLASS.parameterizedBy(self), KModifier.OVERRIDE)
            .initializer("%T::class.java", self)
            .build()

        // `value.validate()` resuelve a la extension generada: vive en ESTE mismo paquete.
        val validateFn = FunSpec.builder("validate")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("value", self)
            .returns(RESULT.parameterizedBy(self))
            .addStatement("return value.validate()")
            .build()

        val adapter = TypeSpec.classBuilder(name)
            .addModifiers(KModifier.PUBLIC)
            .addKdoc(
                "Adaptador generado por kvalid: expone el `validate()` de [%T] como " +
                    "[%T], para bordes que resuelven el validador por tipo en runtime.",
                self, SPI,
            )
            .addSuperinterface(SPI.parameterizedBy(self))
            .apply { if (componentModel == ComponentModel.SPRING) addAnnotation(SPRING_COMPONENT) }
            .addProperty(typeProp)
            .addFunction(validateFn)
            .build()

        return FileSpec.builder(pkg, name).addType(adapter).build().toGeneratedFile()
    }

    private fun packageOf(type: TypeRef): String =
        type.packageName ?: type.qualifiedName.substringBeforeLast('.', "")

    /** Mismo criterio que `ValidationEmitter`: soporta tipos anidados. */
    private fun simpleNamesOf(type: TypeRef): List<String> {
        val pkg = packageOf(type)
        return type.qualifiedName.removePrefix(if (pkg.isEmpty()) "" else "$pkg.").split(".")
    }
}
