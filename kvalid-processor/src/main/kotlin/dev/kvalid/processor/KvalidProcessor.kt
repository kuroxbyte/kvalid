package dev.kvalid.processor

import com.google.devtools.ksp.processing.CodeGenerator
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

public class KvalidProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        KvalidProcessor(environment.codeGenerator, environment.logger)
}

/**
 * Cablea kspkit-ksp con kvalid-core: KSP → `ClassModel` → `ValidationModel` →
 * `GeneratedFile` → `CodeGenerator`. Como el generado de un tipo delega en el `validate`
 * de sus anidados (`v.validate()`), el archivo depende solo de su propia fuente.
 */
public class KvalidProcessor(
    codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val translator = KspTranslator()
    private val writer = KspCodeWriter(codeGenerator)
    private val emitter = ValidationEmitter()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val typeResolver = KspTypeResolver(resolver, translator)

        resolver.getSymbolsWithAnnotation(ValidationNames.VALIDATED)
            .filterIsInstance<KSClassDeclaration>()
            .forEach { decl ->
                val reporter = KspDiagnosticReporter(logger)
                val classModel = translator.translate(decl)
                val model = ValidationModelBuilder(typeResolver, reporter).build(classModel)
                if (!reporter.hasErrors()) {
                    writer.write(emitter.emit(model), setOfNotNull(classModel.source))
                }
            }
        return emptyList()
    }
}
