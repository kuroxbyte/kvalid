package dev.kvalid.processor

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import dev.genkit.emit.toGeneratedFile
import dev.genkit.model.TypeRef
import dev.genkit.ports.GeneratedFile
import dev.kvalid.core.model.Constraint
import dev.kvalid.core.model.ValidatedField
import dev.kvalid.core.model.ValidationModel

/**
 * Emite `fun Type.validate(): ValidationResult<Type>` con **KotlinPoet** (simétrico a JavaPoet
 * en kvalid-apt). Vive en el frontend; el core (kvalid-core) queda como dominio puro.
 */
internal class ValidationEmitter {

    private companion object {
        const val EMAIL_REGEX_LITERAL = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        const val URL_REGEX_LITERAL = "^https?://\\S+$"
        const val RUNTIME_PKG = "dev.kvalid.runtime"
        const val IF_ADD_VIOLATION = "if (%L) violations += %L"
        val VIOLATION = ClassName(RUNTIME_PKG, "Violation")
        val RESULT = ClassName(RUNTIME_PKG, "ValidationResult")
        val CONTEXT = ClassName(RUNTIME_PKG, "ValidationContext")
        val DIGITS = ClassName(RUNTIME_PKG, "Digits")
        val REGEX = ClassName("kotlin.text", "Regex")
    }

    fun emit(model: ValidationModel): GeneratedFile {
        val type = model.type
        val pkg = type.packageName ?: type.qualifiedName.substringBeforeLast('.', "")
        val simpleNames = type.qualifiedName.removePrefix(if (pkg.isEmpty()) "" else "$pkg.").split(".")
        val self = ClassName(pkg, simpleNames)

        val needsEmail = model.fields.any { f -> f.constraints.any { it is Constraint.Email } }
        val needsUrl = model.fields.any { f -> f.constraints.any { it is Constraint.Url } }
        val patterns = model.fields.flatMap { f -> f.constraints.filterIsInstance<Constraint.Pattern>().map { f.name to it.regex } }
        val usesContext = model.classValidators.isNotEmpty() ||
            model.fields.any { f -> f.constraints.any { it is Constraint.Custom } }

        val body = CodeBlock.builder()
        body.addStatement("val violations = mutableListOf<%T>()", VIOLATION)
        if (usesContext) body.addStatement("val ctx = %T()", CONTEXT)
        model.fields.forEach { field -> emitField(body, field) }
        model.classValidators.forEach { cv ->
            body.addStatement("%L.validate(this, %S, ctx, %L)", cv.validatorFqn, "", paramsLiteral(cv.params))
        }
        if (usesContext) body.addStatement("violations += ctx.violations")
        body.addStatement(
            "return if (violations.isEmpty()) %T.Valid(this) else %T.Invalid(violations)",
            RESULT, RESULT,
        )

        val fn = FunSpec.builder("validate")
            .addModifiers(KModifier.PUBLIC)
            .receiver(self)
            .returns(RESULT.parameterizedBy(self))
            .addCode(body.build())
            .build()

        val file = FileSpec.builder(pkg, "${type.simpleName}Validator")
        if (needsEmail) file.addProperty(regexProp("EMAIL_REGEX", EMAIL_REGEX_LITERAL))
        if (needsUrl) file.addProperty(regexProp("URL_REGEX", URL_REGEX_LITERAL))
        patterns.forEach { (name, regex) -> file.addProperty(regexProp("${name}_pattern", regex)) }
        file.addFunction(fn)
        return file.build().toGeneratedFile()
    }

    private fun regexProp(name: String, literal: String): PropertySpec =
        PropertySpec.builder(name, REGEX).addModifiers(KModifier.PRIVATE).initializer("Regex(%S)", literal).build()

    private fun emitField(b: CodeBlock.Builder, field: ValidatedField) {
        val n = field.name

        field.constraints.filterIsInstance<Constraint.NotNull>().forEach { c ->
            b.addStatement("if (this.%N == null) violations += %L", n, violation(CodeBlock.of("%S", n), "notNull", null, c.message))
        }

        field.constraints.filterIsInstance<Constraint.Null>().forEach { c ->
            b.addStatement("if (this.%N != null) violations += %L", n, violation(CodeBlock.of("%S", n), "null", null, c.message))
        }

        // NotNull y Null hablan de la REFERENCIA, así que van fuera del bloque de valor presente.
        val hasValueChecks = field.constraints.any { it !is Constraint.NotNull && it !is Constraint.Null } || field.cascade
        if (hasValueChecks) {
            b.beginControlFlow(if (field.type.nullable) "this.%N?.let { v ->" else "this.%N.let { v ->", n)
            field.constraints.forEach { c -> checkFor(b, field, field.type, c, "v", CodeBlock.of("%S", n)) }
            if (field.cascade) emitCascade(b, "v", CodeBlock.of("%S + ", "$n."))
            b.endControlFlow()
        }

        emitElementChecks(b, field)
    }

    /** Constraints/cascada sobre los ELEMENTOS de una colección (`List<@NotBlank String>`). */
    private fun emitElementChecks(b: CodeBlock.Builder, field: ValidatedField) {
        if (field.elementConstraints.isEmpty() && !field.elementCascade) return
        val elementType = field.type.typeArgs.firstOrNull() ?: return
        val n = field.name
        b.beginControlFlow(if (field.type.nullable) "this.%N?.forEachIndexed { i, e ->" else "this.%N.forEachIndexed { i, e ->", n)
        val pathCode = CodeBlock.of("%S + i + %S", "$n[", "]")
        field.elementConstraints.forEach { c -> checkFor(b, field, elementType, c, "e", pathCode) }
        if (field.elementCascade) emitCascade(b, "e", CodeBlock.of("%S + i + %S + ", "$n[", "]."))
        b.endControlFlow()
    }

    private fun emitCascade(b: CodeBlock.Builder, value: String, pathPrefix: CodeBlock) {
        b.beginControlFlow("when (val r = %L.validate())", value)
        b.addStatement("is %T.Invalid -> violations += r.violations.map { it.copy(path = %L it.path) }", RESULT, pathPrefix)
        b.addStatement("else -> {}")
        b.endControlFlow()
    }

    private fun checkFor(b: CodeBlock.Builder, field: ValidatedField, type: TypeRef, c: Constraint, value: String, path: CodeBlock) {
        val n = field.name
        when (c) {
            is Constraint.NotBlank -> b.addStatement("if (%L.isBlank()) violations += %L", value, violation(path, "notBlank", null, c.message))
            is Constraint.NotEmpty -> b.addStatement("if (%L.isEmpty()) violations += %L", value, violation(path, "notEmpty", null, c.message))
            is Constraint.Email -> b.addStatement("if (!EMAIL_REGEX.matches(%L)) violations += %L", value, violation(path, "email", null, c.message))
            is Constraint.Url -> b.addStatement("if (!URL_REGEX.matches(%L)) violations += %L", value, violation(path, "url", null, c.message))
            is Constraint.OneOf -> {
                val set = c.values.joinToString(", ") { "\"$it\"" }
                b.addStatement("if (%L !in setOf(%L)) violations += %L", value, set, violation(path, "oneOf", null, c.message))
            }
            is Constraint.Pattern -> b.addStatement("if (!%L_pattern.matches(%L)) violations += %L", n, value, violation(path, "pattern", null, c.message))
            is Constraint.Size -> {
                val m = if (type.isStringType()) "length" else "size"
                b.addStatement("if (%L.%L < %L) violations += %L", value, m, c.min, violation(path, "size.min", CodeBlock.of("mapOf(%S to %L)", "min", c.min), c.message))
                b.addStatement("if (%L.%L > %L) violations += %L", value, m, c.max, violation(path, "size.max", CodeBlock.of("mapOf(%S to %L)", "max", c.max), c.message))
            }
            is Constraint.Min -> b.addStatement(IF_ADD_VIOLATION, below(type, value, c.value), violation(path, "min", CodeBlock.of("mapOf(%S to %LL)", "min", c.value), c.message))
            is Constraint.Max -> b.addStatement(IF_ADD_VIOLATION, above(type, value, c.value), violation(path, "max", CodeBlock.of("mapOf(%S to %LL)", "max", c.value), c.message))
            is Constraint.Range -> b.addStatement(
                "if (%L || %L) violations += %L", below(type, value, c.min), above(type, value, c.max),
                violation(path, "range", CodeBlock.of("mapOf(%S to %LL, %S to %LL)", "min", c.min, "max", c.max), c.message),
            )
            is Constraint.DecimalMin -> b.addStatement("if (%L < 0) violations += %L", decimalCmp(value, c.value), violation(path, "decimalMin", CodeBlock.of("mapOf(%S to %S)", "min", c.value), c.message))
            is Constraint.DecimalMax -> b.addStatement("if (%L > 0) violations += %L", decimalCmp(value, c.value), violation(path, "decimalMax", CodeBlock.of("mapOf(%S to %S)", "max", c.value), c.message))
            is Constraint.Past -> b.addStatement(IF_ADD_VIOLATION, temporal(type, value, past = true), violation(path, "past", null, c.message))
            is Constraint.Future -> b.addStatement(IF_ADD_VIOLATION, temporal(type, value, past = false), violation(path, "future", null, c.message))
            is Constraint.Positive -> b.addStatement(IF_ADD_VIOLATION, nonPositive(type, value), violation(path, "positive", null, c.message))
            is Constraint.Negative -> b.addStatement(IF_ADD_VIOLATION, nonNegative(type, value), violation(path, "negative", null, c.message))
            is Constraint.PositiveOrZero -> b.addStatement(IF_ADD_VIOLATION, strictlySigned(type, value, negative = true), violation(path, "positiveOrZero", null, c.message))
            is Constraint.NegativeOrZero -> b.addStatement(IF_ADD_VIOLATION, strictlySigned(type, value, negative = false), violation(path, "negativeOrZero", null, c.message))
            is Constraint.PastOrPresent -> b.addStatement(IF_ADD_VIOLATION, temporalOrPresent(type, value, past = true), violation(path, "pastOrPresent", null, c.message))
            is Constraint.FutureOrPresent -> b.addStatement(IF_ADD_VIOLATION, temporalOrPresent(type, value, past = false), violation(path, "futureOrPresent", null, c.message))
            is Constraint.AssertTrue -> b.addStatement("if (!%L) violations += %L", value, violation(path, "assertTrue", null, c.message))
            is Constraint.AssertFalse -> b.addStatement(IF_ADD_VIOLATION, value, violation(path, "assertFalse", null, c.message))
            is Constraint.Digits -> b.addStatement(
                "if (%T.exceeds(%L, %L, %L)) violations += %L",
                DIGITS, digitsSource(type, value), c.integer, c.fraction,
                violation(path, "digits", CodeBlock.of("mapOf(%S to %L, %S to %L)", "integer", c.integer, "fraction", c.fraction), c.message),
            )
            is Constraint.Custom -> b.addStatement("%L.validate(%L, %S, ctx, %L)", c.validatorFqn, value, n, paramsLiteral(c.params))
            is Constraint.NotNull, is Constraint.Null -> Unit
        }
    }

    /**
     * Texto decimal para `@Digits`. `BigDecimal.toString()` puede salir en notación
     * científica (`1E+10`), que no es contable: para ese tipo se fuerza `toPlainString()`.
     */
    private fun digitsSource(t: TypeRef, v: String): String = when (t.qualifiedName) {
        "kotlin.String" -> v
        "java.math.BigDecimal" -> "$v.toPlainString()"
        else -> "$v.toString()"
    }

    /** Violación de `@PositiveOrZero` (negativo estricto) o de `@NegativeOrZero` (positivo estricto). */
    private fun strictlySigned(t: TypeRef, v: String, negative: Boolean): String {
        val op = if (negative) "<" else ">"
        return when (numericCategory(t)) {
            NumCat.INTEGER -> "$v.toLong() $op 0L"
            NumCat.FLOATING -> "$v.toDouble() $op 0.0"
            NumCat.BIG_INTEGER, NumCat.BIG_DECIMAL -> "$v.signum() $op 0"
        }
    }

    /** Violación de `@PastOrPresent` (está en el futuro) o `@FutureOrPresent` (está en el pasado). */
    private fun temporalOrPresent(t: TypeRef, v: String, past: Boolean): String =
        if (t.qualifiedName == "java.time.Instant") {
            if (past) "$v.isAfter(java.time.Instant.now())" else "$v.isBefore(java.time.Instant.now())"
        } else {
            if (past) "$v > kotlinx.datetime.Clock.System.now()" else "$v < kotlinx.datetime.Clock.System.now()"
        }

    private fun violation(path: CodeBlock, code: String, params: CodeBlock?, message: String): CodeBlock {
        val cb = CodeBlock.builder().add("%T(%L, %S", VIOLATION, path, code)
        if (params != null) cb.add(", %L", params)
        if (message.isNotEmpty()) cb.add(", message = %S", message)
        cb.add(")")
        return cb.build()
    }

    // Comparaciones numéricas en el tipo propio (no toDouble). Devuelven código crudo (%L).
    private fun below(t: TypeRef, v: String, bound: Long): String = when (numericCategory(t)) {
        NumCat.INTEGER -> "$v.toLong() < ${bound}L"
        NumCat.FLOATING -> "$v.toDouble() < ${bound}.0"
        NumCat.BIG_INTEGER -> "$v < java.math.BigInteger.valueOf(${bound}L)"
        NumCat.BIG_DECIMAL -> "$v < java.math.BigDecimal.valueOf(${bound}L)"
    }

    private fun above(t: TypeRef, v: String, bound: Long): String = when (numericCategory(t)) {
        NumCat.INTEGER -> "$v.toLong() > ${bound}L"
        NumCat.FLOATING -> "$v.toDouble() > ${bound}.0"
        NumCat.BIG_INTEGER -> "$v > java.math.BigInteger.valueOf(${bound}L)"
        NumCat.BIG_DECIMAL -> "$v > java.math.BigDecimal.valueOf(${bound}L)"
    }

    private fun nonPositive(t: TypeRef, v: String): String = when (numericCategory(t)) {
        NumCat.INTEGER -> "$v.toLong() <= 0L"
        NumCat.FLOATING -> "$v.toDouble() <= 0.0"
        NumCat.BIG_INTEGER, NumCat.BIG_DECIMAL -> "$v.signum() <= 0"
    }

    private fun nonNegative(t: TypeRef, v: String): String = when (numericCategory(t)) {
        NumCat.INTEGER -> "$v.toLong() >= 0L"
        NumCat.FLOATING -> "$v.toDouble() >= 0.0"
        NumCat.BIG_INTEGER, NumCat.BIG_DECIMAL -> "$v.signum() >= 0"
    }

    private fun decimalCmp(v: String, value: String): String =
        "java.math.BigDecimal($v.toString()).compareTo(java.math.BigDecimal(\"$value\"))"

    private fun temporal(t: TypeRef, v: String, past: Boolean): String =
        if (t.qualifiedName == "java.time.Instant") {
            if (past) "!$v.isBefore(java.time.Instant.now())" else "!$v.isAfter(java.time.Instant.now())"
        } else {
            if (past) "$v >= kotlinx.datetime.Clock.System.now()" else "$v <= kotlinx.datetime.Clock.System.now()"
        }

    private enum class NumCat { INTEGER, FLOATING, BIG_INTEGER, BIG_DECIMAL }

    private fun numericCategory(t: TypeRef): NumCat = when (t.qualifiedName) {
        "kotlin.Double", "kotlin.Float" -> NumCat.FLOATING
        "java.math.BigInteger" -> NumCat.BIG_INTEGER
        "java.math.BigDecimal" -> NumCat.BIG_DECIMAL
        else -> NumCat.INTEGER
    }

    private fun paramsLiteral(params: Map<String, Any?>): String {
        if (params.isEmpty()) return "emptyMap()"
        return "mapOf(${params.entries.joinToString(", ") { (k, v) -> "\"$k\" to ${literalOf(v)}" }})"
    }

    private fun literalOf(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"$v\""
        is Long -> "${v}L"
        is Char -> "'$v'"
        else -> v.toString()
    }

    private fun TypeRef.isStringType(): Boolean = qualifiedName == "kotlin.String"
}
