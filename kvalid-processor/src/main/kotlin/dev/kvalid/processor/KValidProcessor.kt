package dev.kvalid.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.JvmPlatformInfo
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.kspkit.KspCodeWriter
import dev.kspkit.KspDiagnosticReporter
import dev.kspkit.KspTranslator
import dev.kspkit.KspTypeResolver
import dev.kvalid.core.build.ValidationModelBuilder
import dev.kvalid.core.model.ValidationNames

public class KValidProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val raw = environment.options[ComponentModel.OPTION]
        val requested = ComponentModel.parse(raw)
        if (requested == null) {
            environment.logger.warn(
                "[kvalid] valor no reconocido para ${ComponentModel.OPTION}: '$raw'. " +
                    "Valores: none | spring | serviceloader. Se usa 'none'.",
            )
        }
        // El adaptador usa Class<T> (y @Component): solo tiene sentido en JVM. En un target
        // JS/Native de un proyecto KMP la opción se ignora en silencio — así el consumidor
        // puede declararla una sola vez sin romper los demás targets.
        val isJvm = environment.platforms.any { it is JvmPlatformInfo }
        val effective = (requested ?: ComponentModel.NONE).takeIf { isJvm } ?: ComponentModel.NONE

        return KValidProcessor(environment.codeGenerator, environment.logger, effective)
    }
}

/**
 * Cablea kspkit-ksp con kvalid-core: KSP → `ClassModel` → `ValidationModel` →
 * `GeneratedFile` → `CodeGenerator`. Como el generado de un tipo delega en el `validate`
 * de sus anidados (`v.validate()`), el archivo depende solo de su propia fuente.
 *
 * Con [componentModel] distinto de `NONE` emite además un adaptador por tipo
 * (ver [ValidatorAdapterEmitter]); en `SERVICE_LOADER` agrega el `META-INF/services`.
 */
internal class KValidProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val componentModel: ComponentModel = ComponentModel.NONE,
) : SymbolProcessor {

    private val translator = KspTranslator()
    private val writer = KspCodeWriter(codeGenerator)
    private val emitter = ValidationEmitter()
    private val adapterEmitter = ValidatorAdapterEmitter()

    /** FQN de los adaptadores emitidos, para el `META-INF/services` (solo SERVICE_LOADER). */
    private val adapterFqns = sortedSetOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val typeResolver = KspTypeResolver(resolver, translator)

        resolver.getSymbolsWithAnnotation(ValidationNames.VALIDATED)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                val reporter = KspDiagnosticReporter(logger)
                val classModel = translator.translate(decl)
                val model = ValidationModelBuilder(typeResolver, reporter).build(classModel)
                if (!reporter.hasErrors()) {
                    val origins = setOfNotNull(classModel.source)
                    writer.write(emitter.emit(model), origins)
                    if (componentModel != ComponentModel.NONE) {
                        writer.write(adapterEmitter.emit(model.type, componentModel), origins)
                        adapterFqns += adapterEmitter.adapterFqn(model.type)
                    }
                }
            }
        return emptyList()
    }

    /**
     * El `META-INF/services` es **agregador** (una línea por adaptador), así que se escribe una
     * sola vez al final. `KspCodeWriter` fija la extensión `.kt`, por eso aquí se usa el
     * `CodeGenerator` crudo con `createNewFileByPath`.
     */
    override fun finish() {
        if (componentModel != ComponentModel.SERVICE_LOADER || adapterFqns.isEmpty()) return
        codeGenerator.createNewFileByPath(
            dependencies = Dependencies.ALL_FILES,
            path = "META-INF/services/dev.kvalid.runtime.spi.KValidator",
            extensionName = "",
        ).bufferedWriter().use { out -> adapterFqns.forEach(out::appendLine) }
    }
}
