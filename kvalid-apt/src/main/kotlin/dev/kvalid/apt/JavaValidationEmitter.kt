package dev.kvalid.apt

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import dev.genkit.emit.toGeneratedFile
import dev.genkit.model.TypeRef
import dev.genkit.ports.GeneratedFile
import dev.kvalid.core.model.Constraint
import dev.kvalid.core.model.ValidatedField
import dev.kvalid.core.model.ValidationModel
import javax.lang.model.element.Modifier

/**
 * Emite `static ValidationResult<T> validate(T obj)` en **Java** con JavaPoet, reutilizando el
 * `ValidationModel` de kvalid-core. Cubre constraints escalares/String/numéricos/temporales,
 * `@NotNull`, cascada, **Custom** (`@Constraint`, validador Java con constructor sin args),
 * **element-level** (`List<@NotBlank String>`) y **validadores de clase** (cross-field).
 */
public class JavaValidationEmitter {

    private companion object {
        const val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        const val URL_REGEX = "^https?://\\S+$"
        const val RUNTIME_PKG = "dev.kvalid.runtime"
        const val JAVA_UTIL = "java.util"
        const val EMPTY_MAP = "\$T.of()"
        const val IF_ADD_VIOLATION = "if (\$L) violations.add(\$L)"

        val RESULT = ClassName.get(RUNTIME_PKG, "ValidationResult")
        val VALID = ClassName.get(RUNTIME_PKG, "ValidationResult", "Valid")
        val INVALID = ClassName.get(RUNTIME_PKG, "ValidationResult", "Invalid")
        val VIOLATION = ClassName.get(RUNTIME_PKG, "Violation")
        val LIST = ClassName.get(JAVA_UTIL, "List")
        val ARRAY_LIST = ClassName.get(JAVA_UTIL, "ArrayList")
        val MAP = ClassName.get(JAVA_UTIL, "Map")
        val SET = ClassName.get(JAVA_UTIL, "Set")
        val JPATTERN = ClassName.get("java.util.regex", "Pattern")
        val BIG_DECIMAL = ClassName.get("java.math", "BigDecimal")
        val CONTEXT = ClassName.get(RUNTIME_PKG, "ValidationContext")
    }

    public fun emit(model: ValidationModel, accessors: Map<String, String>): GeneratedFile {
        val type = model.type
        val pkg = type.packageName ?: type.qualifiedName.substringBeforeLast('.', "")
        val dataType = ClassName.bestGuess(type.qualifiedName)
        val resultOfSelf = ParameterizedTypeName.get(RESULT, dataType)

        val usesContext = model.classValidators.isNotEmpty() ||
            model.fields.any { f -> (f.constraints + f.elementConstraints).any { it is Constraint.Custom } }

        val body = CodeBlock.builder()
        body.addStatement("\$T<\$T> violations = new \$T<>()", LIST, VIOLATION, ARRAY_LIST)
        if (usesContext) body.addStatement("\$T ctx = new \$T()", CONTEXT, CONTEXT)
        model.fields.forEach { f -> emitField(body, f, accessors.getValue(f.name)) }
        model.classValidators.forEach { cv ->
            body.addStatement("new \$T().validate(obj, \$S, ctx, \$L)", ClassName.bestGuess(cv.validatorFqn), "", paramsJava(cv.params))
        }
        if (usesContext) body.addStatement("violations.addAll(ctx.getViolations())")
        body.beginControlFlow("if (violations.isEmpty())")
        body.addStatement("return new \$T<>(obj)", VALID)
        body.endControlFlow()
        body.addStatement("return (\$T) (\$T) new \$T(violations)", resultOfSelf, TypeName.OBJECT, INVALID)

        val validate = MethodSpec.methodBuilder("validate")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings::class.java).addMember("value", "\$S", "unchecked").build())
            .returns(resultOfSelf)
            .addParameter(dataType, "obj")
            .addCode(body.build())
            .build()

        val cls = TypeSpec.classBuilder("${type.simpleName}Validator")
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        regexFields(model).forEach { cls.addField(it) }
        cls.addMethod(validate)

        return JavaFile.builder(pkg, cls.build()).build().toGeneratedFile()
    }

    private fun regexFields(model: ValidationModel): List<FieldSpec> {
        val fields = mutableListOf<FieldSpec>()
        if (model.fields.any { f -> f.constraints.any { it is Constraint.Email } }) fields += patternField("EMAIL", EMAIL_REGEX)
        if (model.fields.any { f -> f.constraints.any { it is Constraint.Url } }) fields += patternField("URL", URL_REGEX)
        model.fields.forEach { f ->
            f.constraints.filterIsInstance<Constraint.Pattern>().forEach { fields += patternField("${f.name}Pattern", it.regex) }
        }
        return fields
    }

    private fun patternField(name: String, regex: String): FieldSpec =
        FieldSpec.builder(JPATTERN, name, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
            .initializer("\$T.compile(\$S)", JPATTERN, regex).build()

    private fun emitField(b: CodeBlock.Builder, field: ValidatedField, acc: String) {
        val n = field.name
        // @NotNull (fuera del guard de presencia).
        field.constraints.filterIsInstance<Constraint.NotNull>().forEach { c ->
            b.addStatement("if (obj.\$L == null) violations.add(\$L)", acc, violation(n, "notNull", null, c.message))
        }
        val supported = field.constraints.filter { it !is Constraint.NotNull }
        val hasElements = field.elementConstraints.isNotEmpty() || field.elementCascade
        if (supported.isEmpty() && !field.cascade && !hasElements) return

        val v = "${n}Val"
        b.addStatement("var \$L = obj.\$L", v, acc)
        if (field.type.nullable) b.beginControlFlow("if (\$L != null)", v)
        val vio = violationFactory(CodeBlock.of("\$S", n)) // path = "campo"
        supported.forEach { c -> emitCheck(b, field.type, c, v, n, vio) }
        if (field.cascade) {
            val validator = validatorFqn(field.type)
            b.beginControlFlow("for (\$T __c : \$T.validate(\$L).violationsOrEmpty())", VIOLATION, validator, v)
            b.addStatement("violations.add(new \$T(\$S + __c.getPath(), __c.getCode(), __c.getParams(), __c.getMessage()))", VIOLATION, "$n.")
            b.endControlFlow()
        }
        // Element-level: `List<@NotBlank String>` / cascada por elemento.
        if (hasElements) {
            val elemType = field.type.typeArgs.firstOrNull()
            if (elemType != null) {
                val e = "${n}Elem"
                val idx = "${n}I"
                b.addStatement("int \$L = 0", idx)
                b.beginControlFlow("for (var \$L : \$L)", e, v)
                val vioE = violationFactory(CodeBlock.of("\$S + \$L + \$S", "$n[", idx, "]")) // path = "campo[i]"
                field.elementConstraints.filter { it !is Constraint.NotNull }.forEach { c ->
                    emitCheck(b, elemType, c, e, n, vioE)
                }
                if (field.elementCascade) {
                    val validator = validatorFqn(elemType)
                    b.beginControlFlow("for (\$T __c : \$T.validate(\$L).violationsOrEmpty())", VIOLATION, validator, e)
                    b.addStatement("violations.add(new \$T(\$S + \$L + \$S + __c.getPath(), __c.getCode(), __c.getParams(), __c.getMessage()))", VIOLATION, "$n[", idx, "].")
                    b.endControlFlow()
                }
                b.addStatement("\$L++", idx)
                b.endControlFlow()
            }
        }
        if (field.type.nullable) b.endControlFlow()
    }

    /**
     * Crea la fábrica de `Violation` para un path dado (literal `"campo"` a nivel de propiedad,
     * o `"campo[" + i + "]"` a nivel de elemento). Devuelve una lambda `(code, params, message)`.
     */
    private fun violationFactory(pathCode: CodeBlock): (String, CodeBlock?, String) -> CodeBlock =
        { code, params, message ->
            val paramsCode = params ?: CodeBlock.of(EMPTY_MAP, MAP)
            val msgCode = if (message.isEmpty()) CodeBlock.of("null") else CodeBlock.of("\$S", message)
            CodeBlock.of("new \$T(\$L, \$S, \$L, \$L)", VIOLATION, pathCode, code, paramsCode, msgCode)
        }

    private fun emitCheck(
        b: CodeBlock.Builder,
        type: TypeRef,
        c: Constraint,
        v: String,
        n: String,
        vio: (code: String, params: CodeBlock?, message: String) -> CodeBlock,
    ) {
        when (c) {
            is Constraint.NotBlank -> b.addStatement("if (\$L.isBlank()) violations.add(\$L)", v, vio("notBlank", null, c.message))
            is Constraint.NotEmpty -> b.addStatement("if (\$L.isEmpty()) violations.add(\$L)", v, vio("notEmpty", null, c.message))
            is Constraint.Email -> b.addStatement("if (!EMAIL.matcher(\$L).matches()) violations.add(\$L)", v, vio("email", null, c.message))
            is Constraint.Url -> b.addStatement("if (!URL.matcher(\$L).matches()) violations.add(\$L)", v, vio("url", null, c.message))
            is Constraint.OneOf -> {
                val args = c.values.joinToString(", ") { "\"$it\"" }
                b.addStatement("if (!\$T.of(\$L).contains(\$L)) violations.add(\$L)", SET, args, v, vio("oneOf", null, c.message))
            }
            is Constraint.Pattern -> b.addStatement("if (!\$LPattern.matcher(\$L).matches()) violations.add(\$L)", n, v, vio("pattern", null, c.message))
            is Constraint.Size -> {
                val m = if (type.isString()) "length()" else "size()"
                b.addStatement("if (\$L.\$L < \$L) violations.add(\$L)", v, m, c.min, vio("size.min", CodeBlock.of("\$T.of(\$S, \$L)", MAP, "min", c.min), c.message))
                b.addStatement("if (\$L.\$L > \$L) violations.add(\$L)", v, m, c.max, vio("size.max", CodeBlock.of("\$T.of(\$S, \$L)", MAP, "max", c.max), c.message))
            }
            is Constraint.Min -> b.addStatement(IF_ADD_VIOLATION, cmp(type, v, c.value, below = true), vio("min", CodeBlock.of("\$T.of(\$S, \$LL)", MAP, "min", c.value), c.message))
            is Constraint.Max -> b.addStatement(IF_ADD_VIOLATION, cmp(type, v, c.value, below = false), vio("max", CodeBlock.of("\$T.of(\$S, \$LL)", MAP, "max", c.value), c.message))
            is Constraint.Range -> b.addStatement(
                "if (\$L || \$L) violations.add(\$L)", cmp(type, v, c.min, below = true), cmp(type, v, c.max, below = false),
                vio("range", CodeBlock.of("\$T.of(\$S, \$LL, \$S, \$LL)", MAP, "min", c.min, "max", c.max), c.message),
            )
            is Constraint.DecimalMin -> b.addStatement("if (new \$T(String.valueOf(\$L)).compareTo(new \$T(\$S)) < 0) violations.add(\$L)", BIG_DECIMAL, v, BIG_DECIMAL, c.value, vio("decimalMin", CodeBlock.of("\$T.of(\$S, \$S)", MAP, "min", c.value), c.message))
            is Constraint.DecimalMax -> b.addStatement("if (new \$T(String.valueOf(\$L)).compareTo(new \$T(\$S)) > 0) violations.add(\$L)", BIG_DECIMAL, v, BIG_DECIMAL, c.value, vio("decimalMax", CodeBlock.of("\$T.of(\$S, \$S)", MAP, "max", c.value), c.message))
            is Constraint.Positive -> b.addStatement(IF_ADD_VIOLATION, sign(type, v, positive = true), vio("positive", null, c.message))
            is Constraint.Negative -> b.addStatement(IF_ADD_VIOLATION, sign(type, v, positive = false), vio("negative", null, c.message))
            is Constraint.Past -> b.addStatement("if (!\$L.isBefore(java.time.Instant.now())) violations.add(\$L)", v, vio("past", null, c.message))
            is Constraint.Future -> b.addStatement("if (!\$L.isAfter(java.time.Instant.now())) violations.add(\$L)", v, vio("future", null, c.message))
            // Custom: instancia el validador Java (constructor sin args) y empuja al ctx compartido.
            is Constraint.Custom -> b.addStatement("new \$T().validate(\$L, \$S, ctx, \$L)", ClassName.bestGuess(c.validatorFqn), v, n, paramsJava(c.params))
            is Constraint.NotNull -> Unit
        }
    }

    private fun paramsJava(params: Map<String, Any?>): CodeBlock {
        if (params.isEmpty()) return CodeBlock.of(EMPTY_MAP, MAP)
        val cb = CodeBlock.builder().add("\$T.of(", MAP)
        params.entries.forEachIndexed { i, (k, value) ->
            if (i > 0) cb.add(", ")
            cb.add("\$S, \$L", k, literalJava(value))
        }
        cb.add(")")
        return cb.build()
    }

    private fun literalJava(v: Any?): CodeBlock = when (v) {
        null -> CodeBlock.of("null")
        is String -> CodeBlock.of("\$S", v)
        is Long -> CodeBlock.of("\$LL", v)
        is Int -> CodeBlock.of("\$L", v)
        is Boolean -> CodeBlock.of("\$L", v)
        else -> CodeBlock.of("\$S", v.toString())
    }

    private fun violation(path: String, code: String, params: CodeBlock?, message: String): CodeBlock {
        val paramsCode = params ?: CodeBlock.of(EMPTY_MAP, MAP)
        val msgCode = if (message.isEmpty()) CodeBlock.of("null") else CodeBlock.of("\$S", message)
        return CodeBlock.of("new \$T(\$S, \$S, \$L, \$L)", VIOLATION, path, code, paramsCode, msgCode)
    }

    private fun cmp(t: TypeRef, v: String, bound: Long, below: Boolean): String {
        val op = if (below) "<" else ">"
        return when {
            t.qualifiedName == "java.math.BigDecimal" -> "$v.compareTo(java.math.BigDecimal.valueOf(${bound}L)) $op 0"
            t.qualifiedName == "java.math.BigInteger" -> "$v.compareTo(java.math.BigInteger.valueOf(${bound}L)) $op 0"
            t.qualifiedName in FLOATING -> "$v $op ${bound}.0"
            else -> "$v $op ${bound}L"
        }
    }

    private fun sign(t: TypeRef, v: String, positive: Boolean): String {
        val op = if (positive) "<=" else ">="
        return when {
            t.qualifiedName == "java.math.BigDecimal" || t.qualifiedName == "java.math.BigInteger" -> "$v.signum() $op 0"
            t.qualifiedName in FLOATING -> "$v $op 0.0"
            else -> "$v $op 0L"
        }
    }

    private fun validatorFqn(t: TypeRef): ClassName {
        val pkg = t.packageName ?: t.qualifiedName.substringBeforeLast('.', "")
        return ClassName.get(if (pkg.isEmpty()) "" else pkg, "${t.simpleName}Validator")
    }

    private fun TypeRef.isString(): Boolean = qualifiedName == "kotlin.String" || qualifiedName == "java.lang.String"

    private val FLOATING = setOf("kotlin.Double", "kotlin.Float", "double", "float", "java.lang.Double", "java.lang.Float")
}
