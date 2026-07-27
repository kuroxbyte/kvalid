package dev.kvalid.core

import dev.genkit.model.AnnotationArg
import dev.genkit.model.AnnotationModel
import dev.genkit.model.ClassModel
import dev.genkit.model.PropertyModel
import dev.genkit.model.TypeKind
import dev.genkit.model.TypeRef
import dev.kvalid.core.model.ValidationNames

/** Helpers para construir ClassModel neutrales en tests de dominio (sin KSP). */

internal fun type(
    qn: String,
    nullable: Boolean = false,
    kind: TypeKind = TypeKind.OTHER,
    args: List<TypeRef> = emptyList(),
): TypeRef = TypeRef(qn, nullable, kind, args, packageName = qn.substringBeforeLast('.', ""))

internal val STRING = type("kotlin.String")
internal val INT = type("kotlin.Int")

internal fun listType(elementQn: String): TypeRef =
    type("kotlin.collections.List", kind = TypeKind.COLLECTION_LIST, args = listOf(type(elementQn)))

internal fun ann(fqName: String, vararg args: Pair<String, Any?>): AnnotationModel =
    AnnotationModel(fqName, args.associate { (k, v) -> k to AnnotationArg.Primitive(v) })

internal fun prop(name: String, type: TypeRef, vararg annotations: AnnotationModel): PropertyModel =
    PropertyModel(name = name, type = type, annotations = annotations.toList())

internal fun validated(
    qn: String,
    vararg properties: PropertyModel,
    classAnnotations: List<AnnotationModel> = emptyList(),
): ClassModel = ClassModel(
    type = type(qn, kind = TypeKind.DATA_CLASS),
    properties = properties.toList(),
    annotations = listOf(AnnotationModel(ValidationNames.VALIDATED)) + classAnnotations,
)

/** Un `AnnotationModel` con un único argumento `KClass` (para `@Constraint(validatedBy = ...)`). */
internal fun annClass(fqName: String, arg: String, classFqn: String): AnnotationModel =
    AnnotationModel(fqName, mapOf(arg to AnnotationArg.ClassRef(classFqn)))

/**
 * Registra en el universo del resolver la DECLARACIÓN de una anotación de constraint custom
 * `fqName`, meta-anotada con `@Constraint(validatedBy = validatorFqn)`. El builder la resuelve
 * para descubrir el validador.
 */
internal fun constraintAnnotationDecl(fqName: String, validatorFqn: String): ClassModel =
    ClassModel(
        type = type(fqName, kind = TypeKind.OTHER),
        annotations = listOf(annClass(ValidationNames.CONSTRAINT, "validatedBy", validatorFqn)),
    )

/** Declaración de una anotación COMPUESTA `fqName`, con constraints kvalid encima. */
internal fun compositeAnnotationDecl(fqName: String, vararg constraints: AnnotationModel): ClassModel =
    ClassModel(type = type(fqName, kind = TypeKind.OTHER), annotations = constraints.toList())
