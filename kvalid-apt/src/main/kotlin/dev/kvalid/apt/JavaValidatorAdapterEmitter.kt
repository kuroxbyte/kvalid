package dev.kvalid.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import dev.genkit.emit.toGeneratedFile
import dev.genkit.model.TypeRef
import dev.genkit.ports.GeneratedFile
import javax.lang.model.element.Modifier

/**
 * Cómo se descubre el adaptador generado en runtime. Opción de javac
 * `-Akvalid.componentModel=...`.
 *
 * Espejo de `ComponentModel` en kvalid-processor: los dos frontends son simétricos pero
 * independientes (igual que [JavaValidationEmitter] lo es de `ValidationEmitter`). El
 * **nombre de la opción y sus valores deben coincidir** entre ambos.
 */
internal enum class ComponentModel {
    NONE,
    SPRING,
    SERVICE_LOADER,

    ;

    internal companion object {
        const val OPTION: String = "kvalid.componentModel"
        const val SERVICES_PATH: String = "META-INF/services/dev.kvalid.runtime.spi.KvalidValidator"

        /** `null` si el valor no se reconoce (el processor avisa por Messager). */
        fun parse(raw: String?): ComponentModel? = when (raw?.trim()?.lowercase()) {
            null, "", "none" -> NONE
            "spring" -> SPRING
            "serviceloader", "service_loader" -> SERVICE_LOADER
            else -> null
        }
    }
}

/**
 * Emite el adaptador Java que delega en el `XValidator.validate(obj)` generado:
 *
 * ```java
 * @Component                                                  // solo con componentModel=spring
 * public final class AccountKvalidValidator implements KvalidValidator<Account> {
 *   @Override public Class<Account> getType() { return Account.class; }
 *   @Override public ValidationResult<Account> validate(Account value) {
 *     return AccountValidator.validate(value);
 *   }
 * }
 * ```
 *
 * `getType()` es el getter de la propiedad `val type` de la interfaz Kotlin.
 */
internal class JavaValidatorAdapterEmitter {

    private companion object {
        val RESULT = ClassName.get("dev.kvalid.runtime", "ValidationResult")
        val SPI = ClassName.get("dev.kvalid.runtime.spi", "KvalidValidator")
        val SPRING_COMPONENT = ClassName.get("org.springframework.stereotype", "Component")
        val OVERRIDE = AnnotationSpec.builder(Override::class.java).build()
    }

    fun adapterName(type: TypeRef): String = simpleNamesOf(type).joinToString("") + "KvalidValidator"

    fun adapterFqn(type: TypeRef): String {
        val pkg = packageOf(type)
        val name = adapterName(type)
        return if (pkg.isEmpty()) name else "$pkg.$name"
    }

    fun emit(type: TypeRef, componentModel: ComponentModel): GeneratedFile {
        val pkg = packageOf(type)
        val names = simpleNamesOf(type)
        val self = ClassName.get(pkg, names.first(), *names.drop(1).toTypedArray())
        val name = adapterName(type)
        val validatorClass = ClassName.get(pkg, "${type.simpleName}Validator")

        val getType = MethodSpec.methodBuilder("getType")
            .addAnnotation(OVERRIDE)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(ClassName.get(Class::class.java), self))
            .addStatement("return \$T.class", self)
            .build()

        val validate = MethodSpec.methodBuilder("validate")
            .addAnnotation(OVERRIDE)
            .addModifiers(Modifier.PUBLIC)
            .returns(ParameterizedTypeName.get(RESULT, self))
            .addParameter(self, "value")
            .addStatement("return \$T.validate(value)", validatorClass)
            .build()

        val adapter = TypeSpec.classBuilder(name)
            .addJavadoc(
                "Adaptador generado por kvalid: expone {@code \$T.validate} como {@link \$T},\n" +
                    "para bordes que resuelven el validador por tipo en runtime.\n",
                validatorClass, SPI,
            )
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(ParameterizedTypeName.get(SPI, self))
            .apply { if (componentModel == ComponentModel.SPRING) addAnnotation(SPRING_COMPONENT) }
            .addMethod(getType)
            .addMethod(validate)
            .build()

        return JavaFile.builder(pkg, adapter).build().toGeneratedFile()
    }

    private fun packageOf(type: TypeRef): String =
        type.packageName ?: type.qualifiedName.substringBeforeLast('.', "")

    private fun simpleNamesOf(type: TypeRef): List<String> {
        val pkg = packageOf(type)
        return type.qualifiedName.removePrefix(if (pkg.isEmpty()) "" else "$pkg.").split(".")
    }
}
