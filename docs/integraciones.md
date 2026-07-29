# KValid — integraciones

Las integraciones son módulos **opcionales**: el core nunca depende de un framework. Ambas
traducen una `ValidationException` (lanzada por `getOrThrow()` sobre un `ValidationResult`) a un
**400** con el cuerpo de errores.

Ejemplos ejecutables y verificados en [`kvalid-samples`](https://github.com/kuroxbyte/kvalid/tree/main/kvalid-samples)
(`AppKtor.kt` con test end-to-end, `AppSpring.kt`).

## Ktor (`kvalid-ktor`)

```kotlin
dependencies {
    implementation("io.github.kuroxbyte:kvalid-ktor:0.1.0")
}
```

`StatusPages { kvalid() }` registra el manejo de `ValidationException`:

```kotlin
fun Application.kvalidModule() {
    install(ContentNegotiation) { json() }
    install(StatusPages) { kvalid() }          // ← integración

    routing {
        post("/users") {
            val req = call.receive<UserRequest>()
            val user = User(req.name, req.age, req.email).validate().getOrThrow()
            call.respond(mapOf("created" to user.name))
        }
    }
}
```

Un request inválido responde `400` con `ValidationErrorResponse(errors = [...])`; uno válido,
`200`. El status es configurable: `kvalid(status = HttpStatusCode.UnprocessableEntity)`.

## Spring (`kvalid-spring`)

```kotlin
dependencies {
    implementation("io.github.kuroxbyte:kvalid-spring:0.1.0")
}
```

`KValidExceptionHandler` es un `@RestControllerAdvice` que convierte la `ValidationException` en
un `ResponseEntity` 400. Regístralo como bean (component-scan o `@Import`):

```kotlin
@RestController
open class UserController {
    @PostMapping("/users")
    open fun create(@RequestBody req: UserRequest): Map<String, String> {
        val user = User(req.name, req.age, req.email).validate().getOrThrow()
        return mapOf("created" to user.name)
    }
}
// + registrar KValidExceptionHandler:  @Import(KValidExceptionHandler::class)
```

## i18n (`kvalid-i18n`)

Independiente del framework: resuelve `code` + `params` a texto legible. El `message` explícito
del constraint gana; si no, se usa la plantilla del `code` con interpolación de `{param}`.

```kotlin
val es = DefaultMessageResolver(mapOf(
    "notBlank" to "No puede estar vacío",
    "size.max" to "Máximo {max} caracteres",
    "min"      to "Debe ser al menos {min}",
))
user.validate().violationsOrEmpty().forEach { println(es.resolve(it)) }
```

En JVM, `ResourceBundleMessageResolver` toma las plantillas de un `ResourceBundle` (properties
por locale). Conéctalo dentro del handler de Ktor/Spring para respuestas ya localizadas.
