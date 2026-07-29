@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package dev.kvalid.processor

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import dev.kvalid.runtime.ValidationResult
import dev.kvalid.runtime.Violation

internal fun compile(source: String): JvmCompilationResult = compilation(source).compile()

/** La [KotlinCompilation] configurada, para tests que además inspeccionan lo generado. */
internal fun compilation(
    source: String,
    options: Map<String, String> = emptyMap(),
): KotlinCompilation =
    KotlinCompilation().apply {
        sources = listOf(SourceFile.kotlin("Input.kt", source))
        useKsp2()
        symbolProcessorProviders += KValidProcessorProvider()
        kspProcessorOptions = options.toMutableMap()
        inheritClassPath = true
        messageOutputStream = System.out
    }

internal fun compileOk(source: String): JvmCompilationResult {
    val result = compile(source)
    check(result.exitCode == KotlinCompilation.ExitCode.OK) { "La compilación falló:\n${result.messages}" }
    return result
}

/** Invoca `<pkg>.<Simple>ValidatorKt.validate(instance)` y devuelve el ValidationResult. */
internal fun JvmCompilationResult.validate(typeFqn: String, instance: Any): ValidationResult<*> {
    val pkg = typeFqn.substringBeforeLast('.', "")
    val simple = typeFqn.substringAfterLast('.')
    val fileClass = classLoader.loadClass(if (pkg.isEmpty()) "${simple}ValidatorKt" else "$pkg.${simple}ValidatorKt")
    val dataClass = classLoader.loadClass(typeFqn)
    val method = fileClass.getMethod("validate", dataClass)
    return method.invoke(null, instance) as ValidationResult<*>
}

internal fun JvmCompilationResult.instance(typeFqn: String, vararg args: Any?): Any {
    val dataClass = classLoader.loadClass(typeFqn)
    val ctor = dataClass.declaredConstructors.first { it.parameterCount == args.size }
    ctor.isAccessible = true
    return ctor.newInstance(*args)
}

internal fun ValidationResult<*>.violations(): List<Violation> = violationsOrEmpty()
