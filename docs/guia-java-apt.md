# KValid — variante Java (APT)

KSP solo procesa Kotlin. Para **clases Java** existe `kvalid-apt`, un annotation processor de
javac que reutiliza el **mismo dominio** (`kvalid-core`) y el mismo runtime
(`ValidationResult`/`Violation`/`ValidationContext`), con un frontend distinto (`kspkit-apt`:
`javax.lang.model` → `kspkit-model`) y un emisor **JavaPoet**.

## Instalación

```kotlin
// build.gradle.kts (proyecto Java)
dependencies {
    implementation("io.github.kuroxbyte:kvalid-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kvalid-runtime:0.1.0")
    annotationProcessor("io.github.kuroxbyte:kvalid-apt:0.1.0")
}
```

## Qué genera

Por cada tipo Java `@Validated` se genera `<Type>Validator` con:

```java
public static ValidationResult<User> validate(User obj) { ... }
```

Records (accesor `component()`) y POJOs (getters). La normalización de tipos Java → canónicos
Kotlin hace que las reglas de dominio (escritas contra FQNs Kotlin) apliquen igual a la entrada
Java.

## Paridad completa con la variante Kotlin

| Caso                        | Java (APT)                                                          |
|-----------------------------|--------------------------------------------------------------------|
| Constraints built-in        | ✅ String, numéricos (type-aware), temporales, `@NotNull`, `@OneOf`, `@Pattern`… |
| Cascada `@Validated`        | ✅ anidado (`address.street`)                                       |
| **Custom** (`@Constraint`)  | ✅ validador Java con constructor sin argumentos                    |
| **Element-level**           | ✅ `List<@NotBlank String>` vía anotaciones type-use del tipo argumento |
| **Validador de clase**      | ✅ cross-field (`ConstraintValidator<TheClass>`)                    |

## Constraint custom en Java

Un validador reutilizable: una anotación meta-anotada con `@Constraint(validatedBy = ...)` y una
clase Java que implementa `ConstraintValidator<T>` con **constructor sin argumentos** (el
generado hace `new Validator()`).

```java
// La anotación de constraint.
@Constraint(validatedBy = SlugValidator.class)
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.TYPE_USE})
public @interface Slug {}

// El validador (constructor sin args).
public final class SlugValidator implements ConstraintValidator<String> {
    @Override
    public void validate(String value, String field, ValidationContext ctx, Map<String, ?> params) {
        if (!value.matches("[a-z0-9-]+")) ctx.violation(field, "slug", Map.of(), null);
    }
}

@Validated public record Article(@Slug String slug) {}
```

!!! note "Única asimetría con Kotlin"
    En Kotlin el validador es un `object` (singleton) y el generado invoca
    `SlugValidator.validate(...)`. En Java es una clase y el generado hace
    `new SlugValidator().validate(...)`, siguiendo el idioma de cada lenguaje (como los
    `ConstraintValidator` de Jakarta, instanciados por el proveedor). La interfaz
    `ConstraintValidator<T>` y el `ValidationContext` acumulador son los mismos.

## Element-level en Java

Las anotaciones de KValid declaran `@Target` con `TYPE` (→ `TYPE_USE` en Java), así que se
aplican sobre el argumento de tipo:

```java
@Validated
public record Post(List<@NotBlank String> tags) {}
// tags[1]=notBlank, tags[2]=notBlank, ...
```

El frontend APT captura esas anotaciones type-use del *mirror* del argumento y el dominio las
resuelve como constraints de elemento.

## Validador de clase (cross-field)

```java
@Constraint(validatedBy = PasswordsMatchValidator.class)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface PasswordsMatch {}

public final class PasswordsMatchValidator implements ConstraintValidator<Signup> {
    @Override
    public void validate(Signup value, String field, ValidationContext ctx, Map<String, ?> params) {
        if (!value.password().equals(value.confirm()))
            ctx.violation("confirm", "passwordsMatch", Map.of(), null);
    }
}

@Validated @PasswordsMatch
public record Signup(String password, String confirm) {}
```

## Verificación

`kvalid-apt` trae 6 tests end-to-end (javac + processor): constraints acumulados, cascada,
custom, element-level, cross-field y POJOs. Ver los [ejemplos ejecutables](ejemplos.md).
