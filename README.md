# kvalid

Validación de data classes por anotaciones, generando el validador en compile-time y
**sin reflexión**. Argumento que la competencia no puede dar: las mismas reglas compartidas
en Kotlin Multiplatform entre servidor y cliente, y compatibilidad con GraalVM native-image.

Core de dominio puro (hexagonal) sobre [genkit](../genkit) + frontend KSP2.

## Estado

**v0.1 funcionalmente completo** (`io.github.kuroxbyte:kvalid-*`, Apache-2.0). 55 tests en
verde (runtime + dominio sin compilar + end-to-end con compilación real KSP2 + integraciones).
`kvalid-annotations` y `kvalid-runtime` son **Kotlin Multiplatform** (JVM, JS, Native).
Pendiente solo el ciclo de release (Maven Central) y las coordenadas definitivas.

**Verificado por test:** cero reflexión en el código generado (`ZeroReflectionTest` — base de
la compatibilidad GraalVM native-image) y precisión numérica exacta (comparación en el tipo
propio, no `Double`). El build native-image real es un paso de CI (requiere GraalVM).

Panorama: Konform/Akkurate/Kalidation/Valiktor son DSL o reflexión; Jakarta/Hibernate
Validator es reflexión (el vocabulario de kvalid es el suyo, a propósito, para facilitar la
migración); Micronaut valida en compile-time pero **acoplado a su framework** y solo JVM. El
cuadrante *anotaciones + generación + cero reflexión* **standalone y KMP** está libre.

## Instalación (JVM)

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.1"
}
dependencies {
    implementation("io.github.kuroxbyte:kvalid-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kvalid-runtime:0.1.0")   // ValidationResult, Violation, ValidationContext
    ksp("io.github.kuroxbyte:kvalid-processor:0.1.0")
}
```

```properties
# gradle.properties
ksp.useKSP2=true
```

Requisitos: JDK 17+.

## Módulos

| Módulo               | Rol                                                                   |
|----------------------|-----------------------------------------------------------------------|
| `kvalid-annotations` | Los constraints + `@Validated`. Cero deps, `@Retention(SOURCE)`.       |
| `kvalid-runtime`     | `ValidationResult`, `Violation`, `ValidationContext`. Cero deps de framework. |
| `kvalid-core`        | DOMINIO puro: `ValidationModel`, `Constraint`, build. Sin KSP ni KotlinPoet (candado Konsist). |
| `kvalid-processor`   | COMPOSICIÓN: kspkit + kvalid-core + **emisor KotlinPoet**. Único con `SymbolProcessorProvider`. |
| `kvalid-i18n`        | OPCIONAL: `MessageResolver` — resuelve `code`+`params` a texto (con interpolación `{param}`). |
| `kvalid-ktor`        | OPCIONAL: integración Ktor (`StatusPages.kvalid()` → 400 con las violaciones). |
| `kvalid-spring`      | OPCIONAL: integración Spring (`@RestControllerAdvice` → 400 con las violaciones). |
| `kvalid-apt`         | Variante **Java** (javac annotation processor): clases Java `@Validated` → `XValidator.validate(obj)`. Reutiliza kvalid-core; emite Java (JavaPoet). Paridad completa con KSP. |
| `kvalid-benchmarks`  | JMH: kvalid (codegen) vs Hibernate Validator (reflexión). No se publica. |
| `kvalid-samples`     | Ejemplos EJECUTABLES (Kotlin/KSP + Java/APT + integración Ktor/Spring). `./gradlew :kvalid-samples:run`. No se publica. |
| `kvalid-integration-tests` | Consumidor REAL end-to-end: aplica KSP y llama al `validate()` generado directamente (sin reflexión). No se publica. |
| `kvalid-incremental-tests` | Incrementalidad de KSP (Gradle TestKit): un consumidor real verifica que tocar una clase ajena NO regenera el archivo. No se publica. |

## Documentación

- **Guía de referencia** (constraints, comportamiento, código generado): [docs/referencia.md](docs/referencia.md).
- **Variante Java (APT)** — records, POJOs, custom, element-level, cross-field: [docs/guia-java-apt.md](docs/guia-java-apt.md).
- **Integraciones** (Ktor y Spring, con ejemplos): [docs/integraciones.md](docs/integraciones.md).
- **Ejemplos ejecutables** (Kotlin y Java, un solo `run`): [docs/ejemplos.md](docs/ejemplos.md) · fuente en [`kvalid-samples`](kvalid-samples).
- Sitio (MkDocs Material): `mkdocs serve`. Cambios: [CHANGELOG.md](CHANGELOG.md).

## Uso

```kotlin
@Validated
data class User(
    @NotBlank @Size(max = 80) val name: String,
    @Range(min = 18, max = 120) val age: Int,
    @Email val email: String,
    val address: Address?,        // @Validated → cascada automática
)

when (val r = user.validate()) {
    is ValidationResult.Valid   -> use(r.value)
    is ValidationResult.Invalid -> r.violations.forEach { println("${it.path}: ${it.code}") }
}
```

`validate()` es una **extensión generada** en el mismo paquete que la clase.

## Constraints (v1)

| Constraint            | Aplica a            | `code`                    |
|-----------------------|---------------------|---------------------------|
| `@NotBlank`           | String              | `notBlank`                |
| `@NotEmpty`           | String o colección  | `notEmpty`                |
| `@Size(min, max)`     | String o colección  | `size.min` / `size.max`   |
| `@Pattern(regex)`     | String              | `pattern`                 |
| `@Email`              | String              | `email`                   |
| `@Url`                | String              | `url`                     |
| `@OneOf(...)`         | String              | `oneOf`                   |
| `@Min(value)`         | numérico            | `min`                     |
| `@Max(value)`         | numérico            | `max`                     |
| `@Range(min, max)`    | numérico            | `range`                   |
| `@DecimalMin(value)`  | numérico (String)   | `decimalMin`              |
| `@DecimalMax(value)`  | numérico (String)   | `decimalMax`              |
| `@Positive`           | numérico            | `positive`                |
| `@Negative`           | numérico            | `negative`                |
| `@Past`               | `Instant`           | `past`                    |
| `@Future`             | `Instant`           | `future`                  |
| `@NotNull`            | cualquiera          | `notNull`                 |
| `@Validated`          | clase               | (marca + cascada)         |

Aplicar un constraint a un tipo incompatible (p. ej. `@Range` sobre String) es **error de
compilación** (`kvalid.constraint.type`). Las regex de `@Pattern`/`@Email` se compilan **una
vez a nivel de archivo**, no por invocación.

**Precisión numérica:** las comparaciones se generan **en el tipo propio de la propiedad**
(`Long` compara como `Long`, `BigDecimal` con `compareTo`) — sin pasar por `Double`, así que
no hay pérdida de precisión en los extremos. Para límites decimales exactos usa
`@DecimalMin`/`@DecimalMax` (el valor es un `String`, como en Jakarta), que comparan vía
`BigDecimal` (ignorando el scale: `1.0` == `1.00`).

## Código + mensaje opcional

`Violation` siempre lleva un `code` estable + `params` (para i18n y lógica programática), y
un `message` **opcional** al estilo Jakarta (`@NotBlank(message = "...")`). Si no declaras
message, queda `null` y resuelves el texto por `code`. Lo mejor de ambos mundos: no cierras
i18n, pero tienes un texto por defecto cuando lo quieres.

```kotlin
data class Violation(
    val path: String,
    val code: String,
    val params: Map<String, Any?> = emptyMap(),
    val message: String? = null,
)
// @Size(max = 80, message = "Máx 80")  →  Violation("name", "size.max", {max=80}, "Máx 80")
```

## No es `Result<T>`

Una validación fallida no es una excepción; construir un `Throwable` con stack trace en cada
formulario es caro y falso. Se define un sellado propio, con `map` y combinadores:

```kotlin
sealed interface ValidationResult<out T> {
    data class Valid<T>(val value: T) : ValidationResult<T>
    data class Invalid(val violations: List<Violation>) : ValidationResult<Nothing>
}
```

**Acumula todas** las violaciones, nunca aborta en la primera — un validador que corta al
primer error es inútil para formularios.

## Cascada anidada

Una propiedad cuyo tipo es `@Validated` se valida en cascada; sus violaciones llegan con el
path prefijado (`address.street`). También cascada **en elementos de colección**
(`List<Address>` con `Address @Validated` → `lines[0].street`). Los tipos nullable se validan
solo si están presentes (`this.address?.let { ... }`), salvo `@NotNull`.

## Composición de constraints

Una anotación puede **componer** varios constraints (como en Jakarta). El generado los
expande sobre cada uso — reutilización sin acoplamiento:

```kotlin
@NotBlank @Size(min = 3, max = 20) @Pattern("[a-z0-9_]+")
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Username

@Validated data class Account(@Username val handle: String)   // aplica los 3 constraints
```

## Validación de argumentos en compile-time

Argumentos inválidos son **error de compilación** (`kvalid.constraint.args`), no fallos en
runtime: `@Size(min > max)` o tamaños negativos, `@Pattern` con regex que no compila, y
`@DecimalMin`/`@DecimalMax` con un decimal mal formado.

## Constraints reutilizables — `@Constraint` + `ConstraintValidator`

Estilo Jakarta pero **sin reflexión**: defines una anotación de constraint, la enlazas a un
validador con `@Constraint(validatedBy = ...)`, e implementas `ConstraintValidator<T>` como
`object`. El generado lo invoca directamente (nada de reflexión ni proxies).

```kotlin
@Constraint(SlugValidator::class)
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class Slug

object SlugValidator : ConstraintValidator<String> {
    override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (!value.matches(SLUG)) ctx.violation(field, "slug")
    }
}

@Validated data class Page(@Slug val handle: String)   // valida handle con SlugValidator
```

**Cross-field** (`endDate > startDate`): la misma mecánica, con la anotación en la **clase**
y el validador sobre la propia data class (`T` = la clase). Reemplaza la vieja convención
`validateCustom` por algo type-safe y reutilizable:

```kotlin
@Constraint(DateOkValidator::class)
@Target(AnnotationTarget.CLASS) @Retention(AnnotationRetention.SOURCE)
annotation class DateOk

object DateOkValidator : ConstraintValidator<DateRange> {
    override fun validate(value: DateRange, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        if (value.end < value.start) ctx.violation("end", "date.after", "field" to "start")
    }
}

@DateOk @Validated data class DateRange(val start: LocalDate, val end: LocalDate)
```

El `field` es el nombre del campo (`""` a nivel de clase); el validador empuja al
`ValidationContext`, que acumula y rebasa el path en la cascada.

**Parametrizable:** el 4º argumento `params: Map<String, Any?>` lleva los args (primitivos)
de la anotación, así que un constraint custom puede configurarse:

```kotlin
@Constraint(LengthValidator::class)
@Target(AnnotationTarget.PROPERTY) @Retention(AnnotationRetention.SOURCE)
annotation class Length(val max: Int)

object LengthValidator : ConstraintValidator<String> {
    override fun validate(value: String, field: String, ctx: ValidationContext, params: Map<String, Any?>) {
        val max = params["max"] as Int
        if (value.length > max) ctx.violation(field, "length", "max" to max)
    }
}
```

## Diagnósticos

| Código                    | Cuándo                                                        |
|---------------------------|--------------------------------------------------------------|
| `kvalid.constraint.type`  | constraint sobre un tipo incompatible (error).               |

## Arquitectura

Hexagonal, sobre `genkit` (base neutral) + `kspkit` (frontend KSP):

```
kvalid-annotations   los constraints + @Validated (KMP-ready, SOURCE)
kvalid-runtime       ValidationResult, Violation, ValidationContext (KMP-ready)
kvalid-core          DOMINIO puro: ClassModel → ValidationModel (sin KSP ni KotlinPoet)
  ├── model/   ValidationModel, Constraint (sellado)
  └── build/   ValidationModelBuilder (parseo + aplicabilidad + cascada + custom)
kvalid-processor     kspkit + kvalid-core + emisor KotlinPoet (único con SymbolProcessorProvider)
```

`kvalid-core` no compila si se le agrega KSP o KotlinPoet (regla dura, candado Konsist). El
dominio se testea sin compilar.

## Compilar desde el fuente

```bash
./gradlew build
```

`genkit` se resuelve por composite build en desarrollo, por coordenadas publicadas en release.

## Constraints a nivel de elemento

Se validan los elementos de una colección con anotaciones type-use, por índice:

```kotlin
@Validated data class Post(val tags: List<@NotBlank @Size(max = 5) String>)
// tags[1]: notBlank, tags[2]: size.max, ...
```

## Integración con Ktor (módulo `kvalid-ktor`)

Validas en tu ruta y `StatusPages` convierte el fallo en un 400 con las violaciones:

```kotlin
install(ContentNegotiation) { json() }
install(StatusPages) { kvalid() }          // dev.kvalid.ktor.kvalid()

post("/users") {
    val user = call.receive<User>().validate().getOrThrow()   // lanza ValidationException si falla
    // ... user es válido
}
// respuesta 400: { "errors": [ { "path": "email", "code": "email" }, ... ] }
```

## Integración con Spring (módulo `kvalid-spring`)

```kotlin
@Import(KvalidExceptionHandler::class)   // o component scan
class WebConfig

@PostMapping("/users")
fun create(@RequestBody dto: User): User = dto.validate().getOrThrow()   // 400 automático si falla
```

## Uso desde Java (variante APT)

Para **clases Java** existe `kvalid-apt`, un annotation processor de javac que reutiliza el
mismo dominio (kvalid-core) y **emite Java** (JavaPoet). Funciona con **records y POJOs**:

```java
@Validated
public record User(@NotBlank @Size(max = 5) String name, @Min(18) int age, @Email String email) {}

ValidationResult<User> r = UserValidator.validate(user);
if (r instanceof ValidationResult.Invalid inv) { /* inv.getViolations() */ }
```

```kotlin
dependencies {
    implementation("io.github.kuroxbyte:kvalid-annotations:0.1.0")
    implementation("io.github.kuroxbyte:kvalid-runtime:0.1.0")
    annotationProcessor("io.github.kuroxbyte:kvalid-apt:0.1.0")
}
```

Mismo frontend `aptkit` (`javax.lang.model` → `genkit-model`, normalizando tipos Java a
canónicos) sobre el mismo core. **Paridad completa con la variante Kotlin:** todos los
constraints built-in, `@NotNull`, cascada `@Validated`, **custom** (`@Constraint` con validador
Java de constructor sin argumentos), **element-level** (`List<@NotBlank String>`, vía anotaciones
type-use) y **validadores de clase** (cross-field). Probado end-to-end con javac (`kvalid-apt`:
6 tests e2e). Ejemplos ejecutables en `kvalid-samples`. Ver [docs/guia-java-apt.md](docs/guia-java-apt.md).

La única asimetría con Kotlin es intencional: un validador custom es un `object` en Kotlin
(se invoca `Validator.validate(...)`) y una clase con constructor sin argumentos en Java
(`new Validator().validate(...)`), siguiendo el idioma de cada lenguaje.

## Rendimiento

Benchmark JMH (`./gradlew :kvalid-benchmarks:jmh`) validando un objeto válido, codegen vs
reflexión — kvalid ~**9× más rápido** que Hibernate Validator:

```
Benchmark                               Mode  Cnt    Score   Units
ValidationBenchmark.kvalid              avgt         ~103    ns/op
ValidationBenchmark.hibernateValidator  avgt         ~963    ns/op
```

(Cifras orientativas de una corrida corta; el módulo de benchmarks no se publica.)

## i18n (opcional, módulo `kvalid-i18n`)

```kotlin
val es = DefaultMessageResolver(mapOf(
    "notBlank" to "No puede estar vacío",
    "size.max" to "Máximo {max} caracteres",
))
es.resolve(Violation("name", "size.max", mapOf("max" to 80)))   // "Máximo 80 caracteres"
```

En JVM, `ResourceBundleMessageResolver(ResourceBundle.getBundle("messages", locale))` toma las
plantillas de archivos `.properties` por locale.
