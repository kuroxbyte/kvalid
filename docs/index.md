# kvalid

Validación por anotaciones en **compile-time** y **sin reflexión**, estilo Jakarta pero
Kotlin-first. Cada clase `@Validated` genera su validador; los constraints inválidos para el
tipo son **errores de compilación**, no fallos en runtime.

Core de dominio puro (hexagonal) sobre [kspkit](https://github.com/kuroxbyte/kspkit), con
**dos frontends** sobre el mismo dominio:

- **Kotlin** vía KSP2 → extensión `Type.validate(): ValidationResult<Type>`.
- **Java** vía javac APT → estática `TypeValidator.validate(obj)`.

## Por qué

- **Cero reflexión** → compatible con GraalVM native-image y Kotlin Multiplatform.
- **Acumula, no aborta**: `ValidationResult.Invalid` con **todas** las violaciones (no una a una).
- **No es `Result<T>`**: una validación fallida no construye una excepción con stack trace en
  cada formulario.
- **`code` estable + `params`** para i18n/lógica, con `message` opcional estilo Jakarta.
- **~9× más rápido** que Hibernate Validator en el mismo objeto (ver benchmarks).

## Un vistazo

=== "Kotlin (KSP)"

    ```kotlin
    @Validated
    data class User(
        @NotBlank @Size(max = 20) val name: String,
        @Min(18) val age: Int,
        @Email val email: String,
    )

    when (val r = user.validate()) {
        is ValidationResult.Valid   -> save(r.value)
        is ValidationResult.Invalid -> r.violations.forEach { println("${it.path}: ${it.code}") }
    }
    ```

=== "Java (APT)"

    ```java
    @Validated
    public record User(
        @NotBlank @Size(max = 20) String name,
        @Min(18) int age,
        @Email String email
    ) {}

    ValidationResult<User> r = UserValidator.validate(user);
    for (Violation v : r.violationsOrEmpty()) { /* v.getPath(), v.getCode() */ }
    ```

## Siguiente paso

- La [guía de referencia](referencia.md) cubre los constraints y el comportamiento por temas.
- La [variante Java (APT)](guia-java-apt.md) cubre records, POJOs, custom, element-level y cross-field.
- Las [integraciones](integraciones.md) conectan las violaciones a un 400 en Ktor y Spring.
- Los [ejemplos ejecutables](ejemplos.md) corren todo con un solo comando.
