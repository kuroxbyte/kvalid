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
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * Annotation processor (javac APT) para clases JAVA `@Validated`. Reutiliza kvalid-core
 * (`ValidationModelBuilder`) y emite Java con [JavaValidationEmitter].
 *
 * Con `-Akvalid.componentModel=spring|serviceloader` emite además un adaptador por tipo
 * ([JavaValidatorAdapterEmitter]) — el mismo contrato que en el frontend KSP, para que una
 * app Spring con clases Java tenga `@Valid` nativo igual que una con clases Kotlin.
 */
public class ValidationProcessor : AbstractProcessor() {

    private val translator = AptTranslator()
    private val emitter = JavaValidationEmitter()
    private val adapterEmitter = JavaValidatorAdapterEmitter()

    /** FQN de los adaptadores emitidos, para el `META-INF/services` (solo SERVICE_LOADER). */
    private val adapterFqns = sortedSetOf<String>()

    private val componentModel: ComponentModel by lazy {
        val raw = processingEnv.options[ComponentModel.OPTION]
        ComponentModel.parse(raw) ?: run {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "[kvalid] valor no reconocido para ${ComponentModel.OPTION}: '$raw'. " +
                    "Valores: none | spring | serviceloader. Se usa 'none'.",
            )
            ComponentModel.NONE
        }
    }

    override fun getSupportedAnnotationTypes(): Set<String> = setOf(ValidationNames.VALIDATED)

    override fun getSupportedOptions(): Set<String> = setOf(ComponentModel.OPTION)

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
                    val origins = setOfNotNull(apt.model.source)
                    writer.write(emitter.emit(model, apt.accessors), origins)
                    if (componentModel != ComponentModel.NONE) {
                        writer.write(adapterEmitter.emit(model.type, componentModel), origins)
                        adapterFqns += adapterEmitter.adapterFqn(model.type)
                    }
                }
            }

        // El services es AGREGADOR: se escribe una sola vez, en la última ronda.
        if (roundEnv.processingOver()) writeServicesFile()
        return true
    }

    private fun writeServicesFile() {
        if (componentModel != ComponentModel.SERVICE_LOADER || adapterFqns.isEmpty()) return
        processingEnv.filer
            .createResource(StandardLocation.CLASS_OUTPUT, "", ComponentModel.SERVICES_PATH)
            .openWriter()
            .use { out -> adapterFqns.forEach { out.write(it + "\n") } }
    }
}
