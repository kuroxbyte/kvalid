package dev.kvalid.apt

import dev.aptkit.AptCodeWriter
import dev.aptkit.AptDiagnosticReporter
import dev.aptkit.AptTranslator
import dev.aptkit.AptTypeResolver
import dev.kvalid.core.build.ValidationModelBuilder
import dev.kvalid.core.model.ValidationNames
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement

/**
 * Annotation processor (javac APT) para clases JAVA `@Validated`. Reutiliza kvalid-core
 * (`ValidationModelBuilder`) y emite Java con [JavaValidationEmitter].
 */
public class ValidationProcessor : AbstractProcessor() {

    private val translator = AptTranslator()
    private val emitter = JavaValidationEmitter()

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(ValidationNames.VALIDATED)

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val validated = processingEnv.elementUtils.getTypeElement(ValidationNames.VALIDATED) ?: return false
        val resolver = AptTypeResolver(processingEnv.elementUtils, translator)
        val writer = AptCodeWriter(processingEnv.filer)

        roundEnv.getElementsAnnotatedWith(validated)
            .filterIsInstance<TypeElement>()
            .forEach { element ->
                val reporter = AptDiagnosticReporter(processingEnv.messager)
                val apt = translator.translate(element)
                val model = ValidationModelBuilder(resolver, reporter).build(apt.model)
                if (!reporter.hasErrors()) {
                    writer.write(emitter.emit(model, apt.accessors), setOfNotNull(apt.model.source))
                }
            }
        return true
    }
}
