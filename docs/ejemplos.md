# kvalid — ejemplos ejecutables

El módulo [`kvalid-samples`](https://github.com/kuroxbyte/kvalid/tree/main/kvalid-samples)
compila y corre de verdad las dos variantes más las integraciones:

- `src/main/kotlin` → variante **Kotlin (KSP)** (constraints, custom, element-level, cross-field, i18n).
- `src/main/java` → variante **Java (APT)**.
- `AppKtor.kt` / `AppSpring.kt` → integraciones (Ktor con test end-to-end).

```bash
./gradlew :kvalid-samples:run     # demo de consola
./gradlew :kvalid-samples:test    # test de integración Ktor (200 / 400)
```

## Salida de la demo (extracto)

```
========== kvalid — variante KOTLIN (KSP) ==========
User válido → OK
User inválido → name=notBlank, age=min, email=email
Article slug válido → OK
Article slug inválido → slug=slug
Post tags → tags[1]=notBlank
Signup passwords → confirm=passwordsMatch
Mensajes i18n (es) para 'User inválido':
  name: No puede estar vacío
  age: Debe ser al menos 18
  email: Email no válido

========== kvalid — variante JAVA (APT) ==========
JavaUser válido → OK
JavaUser inválido → name=notBlank, age=min, email=email
JavaPost tags → tags[1]=notBlank
```

## Constraint custom, element-level y cross-field (Kotlin)

```kotlin
@Constraint(SlugValidator::class)
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class Slug

object SlugValidator : ConstraintValidator<String> {
    private val SLUG = Regex("[a-z0-9-]+")
    override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (!SLUG.matches(value)) ctx.violation(field, "slug")
    }
}

@Validated data class Article(@Slug val slug: String)
@Validated data class Post(val tags: List<@NotBlank String>)   // element-level

@Constraint(PasswordsMatchValidator::class)
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class PasswordsMatch

object PasswordsMatchValidator : ConstraintValidator<Signup> {
    override fun validate(value: Signup, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (value.password != value.confirm) ctx.violation("confirm", "passwordsMatch")
    }
}

@Validated @PasswordsMatch data class Signup(val password: String, val confirm: String)
```

Los mismos casos en Java (records, validadores con constructor sin argumentos) están en
`src/main/java` — ver la [guía de la variante Java](guia-java-apt.md).
