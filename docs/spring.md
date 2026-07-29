# Spring Boot: `@Valid` nativo

Con el starter, **`@Valid @RequestBody` valida con KValid** sin que escribas ni un
`validate()` ni un handler de errores. Funciona igual en **Spring MVC** y en **WebFlux**, y
con DTOs **Kotlin (KSP)** o **Java (APT)** — incluso mezclados en la misma app.

> Todo el código de esta página está respaldado por el módulo ejecutable
> [`kvalid-samples-spring`](https://github.com/kuroxbyte/kvalid/tree/main/kvalid-samples-spring):
> `./gradlew :kvalid-samples-spring:run`.

---

## 1 · Dependencias

```kotlin
plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}

dependencies {
    implementation("io.github.kuroxbyte:kvalid-annotations:0.3.0")
    implementation("io.github.kuroxbyte:kvalid-runtime:0.3.0")
    implementation("io.github.kuroxbyte:kvalid-spring-boot-starter:0.3.0")
    ksp("io.github.kuroxbyte:kvalid-processor:0.3.0")          // DTOs Kotlin
    // annotationProcessor("io.github.kuroxbyte:kvalid-apt:0.3.0")   // DTOs Java

    implementation("org.springframework.boot:spring-boot-starter-web")   // o -webflux
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")    // solo la anotación @Valid
}
```

!!! warning "El paso que no se puede olvidar"
    Hay que pedir explícitamente el adaptador de Spring. **Si falta, no se genera nada y
    `@Valid` no valida — sin ningún error visible.**

    ```kotlin
    ksp { arg("kvalid.componentModel", "spring") }                       // Kotlin

    tasks.withType<JavaCompile>().configureEach {                        // Java
        options.compilerArgs.add("-Akvalid.componentModel=spring")
    }
    ```

!!! note "No necesitas Hibernate Validator"
    Basta `jakarta.validation-api` (la **anotación** `@Valid`). No hace falta
    `spring-boot-starter-validation`: Hibernate Validator es precisamente lo que KValid
    sustituye. Si ya lo usas, **conviven** — ver [Coexistencia](#7-coexistencia-con-jakarta-bean-validation).

## 2 · El DTO y el controlador

=== "Kotlin (KSP)"

    ```kotlin
    @Validated
    data class CreateUserRequest(
        @NotBlank @Size(max = 40) val name: String,
        @Email val email: String,
        @Min(18) val age: Int,
    )

    @RestController
    class UsersController {
        @PostMapping("/users")
        fun create(@Valid @RequestBody req: CreateUserRequest) = mapOf("created" to req.name)
    }
    ```

=== "Java (APT)"

    ```java
    @Validated
    public record CreateOrderRequest(@NotBlank String reference, @Min(1) int quantity) {}

    @RestController
    public class OrdersController {
        @PostMapping("/orders")
        public Map<String, Object> create(@Valid @RequestBody CreateOrderRequest req) {
            return Map.of("reference", req.reference());
        }
    }
    ```

Eso es todo: **ni `validate()` ni `getOrThrow()`**.

## 3 · Qué se genera

Además del `validate()` de siempre, el processor emite un **adaptador** que solo delega:

```kotlin
@Component
public class CreateUserRequestKValidator : KValidator<CreateUserRequest> {
  override val type: Class<CreateUserRequest> = CreateUserRequest::class.java
  override fun validate(value: CreateUserRequest): ValidationResult<CreateUserRequest> =
      value.validate()
}
```

Existe porque el `validate()` generado es una *extension function*, y una extension **no se
puede despachar** desde un borde que recibe `Any` (como el `Validator` de Spring). El
adaptador cierra ese hueco sin duplicar lógica: la validación sigue viviendo en un solo sitio.

## 4 · Cómo encaja con Spring

```
POST /users  {json inválido}
  → Jackson deserializa el DTO
  → @Valid dispara el Validator SPI de Spring
      → KValidSpringValidator → registry[CreateUserRequest] → el adaptador @Component
      → validate()  (código generado, cero reflexión)
      → violaciones → errors.rejectValue(path, code, args, message)
  → BindingResult con errores → MethodArgumentNotValidException → 400
  (el método del controlador NUNCA se ejecuta)
```

La clave del diseño: **se implementa `org.springframework.validation.Validator`**, el SPI que
alimenta `@Valid`. Por eso **un solo adaptador sirve a MVC y a WebFlux**, y los errores entran
en el `BindingResult` estándar — tus `@ExceptionHandler` de siempre siguen funcionando.

## 5 · Qué responde el servidor

Por defecto obtienes un **400**, pero el `ProblemDetail` de Boot **no incluye los campos**:

```json
{"type":"about:blank","title":"Bad Request","status":400,
 "detail":"Invalid request content.","instance":"/users"}
```

Los errores **sí** están en el `BindingResult`:

| field | code | args |
|---|---|---|
| `name` | `notBlank` | `[]` |
| `email` | `email` | `[]` |
| `age` | `min` | `[18]` |

Para exponerlos, un advice de diez líneas (esto es de Spring, no de KValid — con Jakarta harías
lo mismo):

```kotlin
@RestControllerAdvice
class ValidationErrorAdvice {
    data class FieldErrorDto(val field: String, val code: String, val message: String?)

    @ExceptionHandler(MethodArgumentNotValidException::class)   // WebFlux: WebExchangeBindException
    fun onInvalid(ex: MethodArgumentNotValidException) =
        ResponseEntity.badRequest().body(
            mapOf("errors" to ex.bindingResult.fieldErrors.map {
                FieldErrorDto(it.field, it.code ?: "invalid", it.defaultMessage)
            }),
        )
}
```

Salida real del sample (`curl -XPOST localhost:8080/users -d '{"name":"","email":"nope","age":15}'`):

```json
{"errors":[{"field":"name","code":"notBlank","message":"notBlank"},
           {"field":"email","code":"email","message":"email"},
           {"field":"age","code":"min","message":"min"}]}
```

`message` cae al `code` cuando el constraint no declara uno; con un `MessageSource` se
traduce (§6).

## 6 · Mensajes e i18n

El `code` y los `params` viajan como `MessageSourceResolvable`, así que el `MessageSource` de
Spring funciona sin nada más:

```properties
# messages.properties
notBlank=Campo obligatorio
min.age=La edad mínima es {0}
```

Spring prueba `min.createUserRequest.age` → `min.age` → `min`. Si un constraint no declara
`message`, el `defaultMessage` es el propio **code** (nunca nulo: con nulo, Spring no puede
renderizar el `ProblemDetail` y el cuerpo del 400 se iría vacío).

## 7 · Coexistencia con Jakarta Bean Validation

Si además usas Hibernate Validator, **los dos validan**: el starter compone su validador con
el `defaultValidator` de Boot en vez de sustituirlo, así tus `@NotNull` de Jakarta siguen
aplicándose. Los errores de ambos se acumulan en el mismo `BindingResult`.

## 8 · WebFlux

**Sin cambios en el código.** Cambia `spring-boot-starter-web` por `spring-boot-starter-webflux`
y ya: el starter detecta el tipo de aplicación y registra `WebFluxConfigurer` en vez de
`WebMvcConfigurer`. Lo único distinto es el tipo en tu `@ExceptionHandler`
(`WebExchangeBindException`).

## 9 · Configuración

| Property | Default | Para qué |
|---|---|---|
| `kvalid.enabled` | `true` | Apagar toda la integración. |
| `kvalid.web.register-validator` | `true` | Registrar el validador global. **Ponlo en `false` si ya registras el tuyo**: Spring falla si dos `WebMvcConfigurer` devuelven validador. |

## 10 · Sigue estando el estilo explícito

Fuera de un controlador (servicios, jobs, mensajería) el camino de siempre no cambia:

```kotlin
val user = req.validate().getOrThrow()   // Invalid → ValidationException
```

El starter registra `KValidExceptionHandler`, que traduce esa `ValidationException` a un 400
con las violaciones. Los dos caminos conviven.

## Problemas frecuentes

| Síntoma | Causa | Arreglo |
|---|---|---|
| `@Valid` no valida y no hay error | Falta `kvalid.componentModel=spring`: no se generó ningún adaptador | Añadir el `arg`/`-A` del §1 |
| `IllegalStateException: A single Validator is expected` al arrancar | Otro `WebMvcConfigurer` también devuelve validador | `kvalid.web.register-validator=false` |
| El 400 llega sin detalle de campos | Comportamiento por defecto de Boot | Añadir el advice del §5 |
| Un DTO **Java** no valida | Falta `annotationProcessor(kvalid-apt)` y su `-A` | KSP **no** procesa tipos Java a propósito: son de APT |
