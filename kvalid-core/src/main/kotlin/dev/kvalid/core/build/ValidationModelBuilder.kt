package dev.kvalid.core.build

import dev.genkit.model.AnnotationArg
import dev.genkit.model.AnnotationModel
import dev.genkit.model.ClassModel
import dev.genkit.model.PropertyModel
import dev.genkit.model.TypeKind
import dev.genkit.model.TypeRef
import dev.genkit.ports.DiagnosticReporter
import dev.genkit.ports.TypeResolver
import dev.kvalid.core.model.ClassValidator
import dev.kvalid.core.model.Constraint
import dev.kvalid.core.model.ValidatedField
import dev.kvalid.core.model.ValidationDiagnostics
import dev.kvalid.core.model.ValidationModel
import dev.kvalid.core.model.ValidationNames

/**
 * Construye el [ValidationModel] a partir del [ClassModel] raíz `@Validated`. Puro:
 * solo modelo neutral + puertos. Acumula diagnósticos (no aborta al primer error).
 */
public class ValidationModelBuilder(
    private val resolver: TypeResolver,
    private val reporter: DiagnosticReporter,
) {
    public fun build(root: ClassModel): ValidationModel {
        val fields = root.properties.map { prop ->
            ValidatedField(
                name = prop.name,
                type = prop.type,
                constraints = constraintsOf(prop),
                cascade = resolver.isAnnotatedWith(prop.type, ValidationNames.VALIDATED),
                elementConstraints = elementConstraintsOf(prop),
                elementCascade = isElementValidated(prop),
            )
        }
        // Validadores cross-field: anotaciones de CLASE meta-anotadas con @Constraint.
        val classValidators = root.annotations.mapNotNull { ann ->
            customValidatorFqn(ann)?.let { ClassValidator(it, primitiveParams(ann)) }
        }
        return ValidationModel(root.type, fields, classValidators)
    }

    private fun constraintsOf(prop: PropertyModel): List<Constraint> =
        prop.annotations.flatMap { ann -> expand(prop, ann, visited = emptySet()) }

    /**
     * Constraints sobre los ELEMENTOS de una colección (`List<@NotBlank String>`). Las
     * anotaciones type-use viven en el [TypeRef] del argumento de tipo. Se valida su
     * aplicabilidad contra el tipo del elemento (no el de la colección). Custom sobre
     * elementos queda fuera de v1.
     */
    private fun elementConstraintsOf(prop: PropertyModel): List<Constraint> {
        if (prop.type.kind !in ELEMENT_COLLECTION_KINDS) return emptyList()
        val element = prop.type.typeArgs.firstOrNull() ?: return emptyList()
        if (element.annotations.isEmpty()) return emptyList()
        val synthetic = PropertyModel(name = prop.name, type = element, annotations = element.annotations, source = prop.source)
        return element.annotations.flatMap { ann -> expand(synthetic, ann, visited = emptySet()) }
            .filterNot { it is Constraint.Custom }
    }

    /** true si la propiedad es una colección cuyo elemento es `@Validated` (cascada por elemento). */
    private fun isElementValidated(prop: PropertyModel): Boolean {
        if (prop.type.kind !in ELEMENT_COLLECTION_KINDS) return false
        val element = prop.type.typeArgs.firstOrNull() ?: return false
        return resolver.isAnnotatedWith(element, ValidationNames.VALIDATED)
    }

    /**
     * Expande una anotación a constraints. Si es built-in o custom (`@Constraint`), la devuelve
     * directa. Si es una anotación **compuesta** (su declaración lleva constraints kvalid encima,
     * p. ej. `@Username = @NotBlank + @Size`), expande los suyos recursivamente. [visited] corta
     * ciclos de composición.
     */
    private fun expand(prop: PropertyModel, ann: AnnotationModel, visited: Set<String>): List<Constraint> {
        constraintFrom(prop, ann)?.let { return listOf(it) }
        if (ann.qualifiedName in visited) return emptyList()
        val decl = resolver.resolve(TypeRef(ann.qualifiedName)) ?: return emptyList()
        return decl.annotations.flatMap { expand(prop, it, visited + ann.qualifiedName) }
    }

    private fun constraintFrom(prop: PropertyModel, ann: AnnotationModel): Constraint? {
        val msg = ann.stringArg("message") ?: ""
        return when (ann.qualifiedName) {
            ValidationNames.NOT_BLANK -> requireString(prop, "@NotBlank") { Constraint.NotBlank(msg) }
            ValidationNames.EMAIL -> requireString(prop, "@Email") { Constraint.Email(msg) }
            ValidationNames.URL -> requireString(prop, "@Url") { Constraint.Url(msg) }
            ValidationNames.ONE_OF -> requireString(prop, "@OneOf") { Constraint.OneOf(ann.stringArrayArg("values"), msg) }
            ValidationNames.PATTERN -> requireString(prop, "@Pattern") {
                val regex = ann.stringArg("regex") ?: ""
                if (!regexCompiles(regex)) argsError(prop, "@Pattern tiene una regex inválida: '$regex'.")
                else Constraint.Pattern(regex, msg)
            }
            ValidationNames.NOT_EMPTY -> requireStringOrCollection(prop, "@NotEmpty") { Constraint.NotEmpty(msg) }
            ValidationNames.SIZE -> requireStringOrCollection(prop, "@Size") {
                val min = ann.intArg("min", 0)
                val max = ann.intArg("max", Int.MAX_VALUE)
                when {
                    min < 0 || max < 0 -> argsError(prop, "@Size no admite tamaños negativos (min=$min, max=$max).")
                    min > max -> argsError(prop, "@Size con min > max (min=$min, max=$max).")
                    else -> Constraint.Size(min, max, msg)
                }
            }
            ValidationNames.MIN -> requireNumeric(prop, "@Min") { Constraint.Min(ann.longArg("value", 0), msg) }
            ValidationNames.MAX -> requireNumeric(prop, "@Max") { Constraint.Max(ann.longArg("value", 0), msg) }
            ValidationNames.RANGE -> requireNumeric(prop, "@Range") {
                val min = ann.longArg("min", 0)
                val max = ann.longArg("max", 0)
                if (min > max) argsError(prop, "@Range con min > max (min=$min, max=$max).")
                else Constraint.Range(min, max, msg)
            }
            ValidationNames.DECIMAL_MIN -> requireNumeric(prop, "@DecimalMin") {
                decimalOrError(prop, ann.stringArg("value") ?: "0")?.let { Constraint.DecimalMin(it, msg) }
            }
            ValidationNames.DECIMAL_MAX -> requireNumeric(prop, "@DecimalMax") {
                decimalOrError(prop, ann.stringArg("value") ?: "0")?.let { Constraint.DecimalMax(it, msg) }
            }
            ValidationNames.PAST -> requireInstant(prop, "@Past") { Constraint.Past(msg) }
            ValidationNames.FUTURE -> requireInstant(prop, "@Future") { Constraint.Future(msg) }
            ValidationNames.POSITIVE -> requireNumeric(prop, "@Positive") { Constraint.Positive(msg) }
            ValidationNames.NEGATIVE -> requireNumeric(prop, "@Negative") { Constraint.Negative(msg) }
            ValidationNames.NOT_NULL -> Constraint.NotNull(msg)
            else -> customValidatorFqn(ann)?.let { Constraint.Custom(it, primitiveParams(ann), msg) }
        }
    }

    /** Args primitivos de una anotación (excluye `message`), para validadores parametrizables. */
    private fun primitiveParams(ann: AnnotationModel): Map<String, Any?> =
        ann.arguments
            .filterKeys { it != "message" }
            .mapNotNull { (k, v) -> (v as? AnnotationArg.Primitive)?.let { k to it.value } }
            .toMap()

    /**
     * Si [ann] es una anotación de constraint del usuario (su declaración está meta-anotada
     * con `@Constraint(validatedBy = V)`), devuelve el FQN de V. Si no, null.
     */
    private fun customValidatorFqn(ann: AnnotationModel): String? {
        val declaration = resolver.resolve(TypeRef(ann.qualifiedName)) ?: return null
        val meta = declaration.annotation(ValidationNames.CONSTRAINT) ?: return null
        return (meta.arguments["validatedBy"] as? AnnotationArg.ClassRef)?.fqName
    }

    // ── Validaciones de aplicabilidad ────────────────────────────────────────────

    private inline fun requireString(prop: PropertyModel, name: String, make: () -> Constraint?): Constraint? =
        if (prop.type.isString()) make() else mismatch(prop, name, "String")

    private inline fun requireStringOrCollection(
        prop: PropertyModel,
        name: String,
        make: () -> Constraint?,
    ): Constraint? =
        if (prop.type.isString() || prop.type.isCollection()) make() else mismatch(prop, name, "String o colección")

    private inline fun requireNumeric(prop: PropertyModel, name: String, make: () -> Constraint?): Constraint? =
        if (prop.type.isNumeric()) make() else mismatch(prop, name, "un tipo numérico")

    private inline fun requireInstant(prop: PropertyModel, name: String, make: () -> Constraint?): Constraint? =
        if (prop.type.qualifiedName in INSTANT_TYPES) make() else mismatch(prop, name, "un Instant")

    private fun mismatch(prop: PropertyModel, constraint: String, expected: String): Constraint? {
        reporter.error(
            ValidationDiagnostics.CONSTRAINT_TYPE,
            "$constraint no aplica a '${prop.name}: ${prop.type.qualifiedName}': se esperaba $expected.",
            prop.source,
        )
        return null
    }

    /** Reporta argumentos de constraint inválidos y devuelve null (se omite el constraint). */
    private fun argsError(prop: PropertyModel, message: String): Constraint? {
        reporter.error(ValidationDiagnostics.CONSTRAINT_ARGS, "$message (propiedad '${prop.name}')", prop.source)
        return null
    }

    private fun regexCompiles(regex: String): Boolean =
        runCatching { Regex(regex) }.isSuccess

    private fun decimalOrError(prop: PropertyModel, value: String): String? =
        if (runCatching { java.math.BigDecimal(value) }.isSuccess) value
        else {
            argsError(prop, "valor decimal mal formado: '$value'.")
            null
        }

    // ── Helpers de tipo ──────────────────────────────────────────────────────────

    private fun TypeRef.isString(): Boolean = qualifiedName == "kotlin.String"

    private fun TypeRef.isCollection(): Boolean = kind in COLLECTION_KINDS

    private fun TypeRef.isNumeric(): Boolean = qualifiedName in NUMERIC_TYPES

    private fun AnnotationModel.stringArg(name: String): String? =
        (arguments[name] as? AnnotationArg.Primitive)?.value as? String

    private fun AnnotationModel.intArg(name: String, default: Int): Int =
        ((arguments[name] as? AnnotationArg.Primitive)?.value as? Number)?.toInt() ?: default

    private fun AnnotationModel.longArg(name: String, default: Long): Long =
        ((arguments[name] as? AnnotationArg.Primitive)?.value as? Number)?.toLong() ?: default

    private fun AnnotationModel.stringArrayArg(name: String): List<String> =
        (arguments[name] as? AnnotationArg.ArrayOf)?.values
            ?.mapNotNull { (it as? AnnotationArg.Primitive)?.value as? String }
            ?: emptyList()

    private companion object {
        val COLLECTION_KINDS = setOf(
            TypeKind.COLLECTION_LIST,
            TypeKind.COLLECTION_SET,
            TypeKind.COLLECTION_MAP,
            TypeKind.COLLECTION_ITERABLE,
        )

        /** Colecciones con un elemento iterable por índice (para constraints de elemento). */
        val ELEMENT_COLLECTION_KINDS = setOf(
            TypeKind.COLLECTION_LIST,
            TypeKind.COLLECTION_SET,
            TypeKind.COLLECTION_ITERABLE,
        )
        val NUMERIC_TYPES = setOf(
            "kotlin.Int", "kotlin.Long", "kotlin.Short", "kotlin.Byte",
            "kotlin.Double", "kotlin.Float",
            "java.math.BigDecimal", "java.math.BigInteger",
        )
        val INSTANT_TYPES = setOf("kotlinx.datetime.Instant", "java.time.Instant")
    }
}
