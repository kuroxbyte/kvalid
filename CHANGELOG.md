# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).
Versionado semántico.
## [Sin publicar]

Nada todavía.

## [0.3.0] — 2026-07

### Añadido
- **Los 8 constraints que faltaban para cubrir el vocabulario de Jakarta Validation 3.0:**
  `@Null`, `@AssertTrue`, `@AssertFalse`, `@Digits`, `@PositiveOrZero`, `@NegativeOrZero`,
  `@PastOrPresent` y `@FutureOrPresent`. Disponibles por igual en KSP (Kotlin) y APT (Java).
- **`dev.kvalid.runtime.Digits`**, en `commonMain`: la cuenta de dígitos de `@Digits` vive en el
  runtime y no inline en el generado, para no emitir `java.math.BigDecimal` —inexistente fuera
  de la JVM— y para testear sus casos de borde una sola vez.
- **[docs/jakarta.md](docs/jakarta.md)**: tabla de equivalencias con Jakarta, diferencias de
  comportamiento y el razonamiento de lo que deliberadamente no se soporta.

### Corregido
- **`@DecimalMin`/`@DecimalMax` ya no atan el código generado a la JVM.** Emitían
  `java.math.BigDecimal` para cualquier tipo numérico, así que una propiedad `Double` o `Int`
  en `commonMain` generaba código que **no compilaba** para targets nativos, JS ni Wasm
  (`Unresolved reference: java`). Ahora la comparación depende del tipo, como ya hacían `@Min`
  y `@Positive`: enteros comparan como `Long` con la cota redondeada **en compilación**
  (`v < 0.5` ⟺ `v < 1`), `Double`/`Float` comparan en su propio tipo, y `BigDecimal` solo
  aparece para propiedades que ya son de un tipo exclusivo de la JVM.
- **La cota decimal se construye una vez, no en cada validación.** Antes los dos `BigDecimal`
  —el del valor y el de la cota— se creaban dentro de la condición, en cada llamada a
  `validate()`. La cota es constante, así que ahora se iza a un `private val` de archivo, igual
  que ya se hacía con las regex de `@Email` y `@Pattern`.

### Notas
- `@Digits` **no admite `Double`/`Float`** (igual que la especificación: la coma flotante
  binaria no tiene un número exacto de dígitos decimales) y `@Null` sobre un tipo que no admite
  null son **errores de compilación**, con un mensaje que explica la causa.
- Sigue sin soportarse `groups`, y es una decisión de diseño, no una carencia pendiente: su
  modo de fallo es silencioso (olvidar el grupo hace que los constraints no se ejecuten). El
  razonamiento completo y la alternativa están en [docs/jakarta.md](docs/jakarta.md).

## [0.2.0] — 2026-07

### Añadido
- **`@Valid` nativo en Spring MVC y WebFlux.** `KValidSpringValidator` implementa el
  `Validator` SPI de Spring —el que alimenta `@Valid`—, así que un solo adaptador sirve a los
  dos stacks: las violaciones entran en el `BindingResult` y Spring responde 400 sin que el
  controller llame a `validate()`. Nuevo módulo **`kvalid-spring-boot-starter`** con
  auto-configuración condicional por tipo de app, y `CompositeValidator` para que Jakarta Bean
  Validation (Hibernate Validator) siga aplicándose si también se usa.
- **Opción de codegen `kvalid.componentModel`** (`none` por defecto | `spring` | `serviceloader`),
  en los **dos frontends**: KSP (`ksp { arg(...) }`) y APT (`-Akvalid.componentModel=...`).
  Genera un adaptador `KValidator<T>` por tipo que **delega** en el `validate()` generado —
  necesario porque una *extension function* no se puede despachar desde un borde que recibe
  `Any`. Con `spring` lleva `@Component`; con `serviceloader`, `META-INF/services`.
  En KSP solo se emite en target **JVM**, para no romper los demás targets KMP.

- Módulo `kvalid-samples-spring`: app **Spring Boot ejecutable** (`./gradlew
  :kvalid-samples-spring:run`) con un DTO Kotlin (KSP) y otro Java (APT) **en la misma app**,
  más `docs/spring.md` con el recorrido completo.

### Corregido
- **KSP ya no procesa tipos Java.** En un módulo mixto Kotlin+Java, KSP también ve las clases
  Java `@Validated` y las procesaba: generaba un `validate()` **vacío** (no lee los constraints
  de los componentes de un record), es decir un validador que no validaba nada en silencio, y
  además su adaptador colisionaba con el de APT por tener el mismo FQN. Los tipos Java son de
  `kvalid-apt`.

### Cambiado (BREAKING)
- **Renombrado a la marca `KValid`.** Las clases pasan de `Kvalid*` a `KValid*`, y el SPI queda
  como **`KValidator`** (antes habría sido `KValidValidator`, que tartamudea). Afecta a tipos
  ya publicados en 0.1.0: `KvalidExceptionHandler` → **`KValidExceptionHandler`** y
  `KvalidProcessorProvider` → **`KValidProcessorProvider`**. **No se dejan alias deprecados**:
  al actualizar hay que cambiar el import.
  Lo que **no** cambia: los artefactos (`io.github.kuroxbyte:kvalid-*`), los paquetes
  (`dev.kvalid.*`), las propiedades (`kvalid.enabled`, `kvalid.componentModel`) y la función
  de Ktor `StatusPages.kvalid()`.
- `KvalidProcessor` pasa a ser `internal` (era `public` en 0.1.0). El punto de entrada
  soportado es `KValidProcessorProvider`, que es el que declara `META-INF/services`.

### Añadido
- **Variante Java (APT)** con paridad completa: **custom** (`@Constraint` con validador Java de
  constructor sin argumentos), **element-level** (`List<@NotBlank String>` vía anotaciones
  type-use) y **validadores de clase** (cross-field). Antes solo cubría los constraints
  built-in y la cascada.
- Módulo `kvalid-samples`: ejemplos ejecutables (Kotlin/KSP + Java/APT) e integración
  Ktor/Spring, con test end-to-end de Ktor. `./gradlew :kvalid-samples:run`.
- Documentación estilo MkDocs Material (`mkdocs.yml`, `docs/index.md`, guía de la variante
  Java, integraciones, ejemplos).

### Nota
- La única asimetría intencional entre variantes: un validador custom es un `object` en Kotlin
  y una clase con constructor sin argumentos en Java.

## [0.1.0] — funcionalmente completo (sin publicar en Maven Central)

### Añadido
- Validación por anotaciones en compile-time (KSP2), sin reflexión: `@Validated` genera
  `Type.validate(): ValidationResult<Type>`, que **acumula** todas las violaciones.
- Constraints: `@NotBlank`, `@NotEmpty`, `@Size`, `@Pattern`, `@Email`, `@Url`, `@OneOf`,
  `@Min`, `@Max`, `@Range`, `@DecimalMin`, `@DecimalMax`, `@Positive`, `@Negative`, `@Past`,
  `@Future`, `@NotNull`.
- Rediseño estilo Jakarta: `Violation.message` opcional + `@Constraint`/`ConstraintValidator`
  reutilizable (reemplaza la convención `validateCustom`).
- Precisión numérica type-aware, bounds verificados en build-time, composición de constraints,
  cascada anidada, element-level.
- `kvalid-runtime` (`ValidationResult`/`Violation`/`ValidationContext`/`getOrThrow`) y
  `kvalid-i18n` (`MessageResolver`), KMP.
- Integraciones opcionales: `kvalid-ktor` (`StatusPages.kvalid()`) y `kvalid-spring`
  (`@RestControllerAdvice`).
- Benchmark JMH vs Hibernate Validator (~9×).
- Arquitectura hexagonal sobre `kspkit`, con la frontera del core verificada por Konsist.

### Pendiente
- Ciclo de release a Maven Central (GPG, Sonatype, CI) y build native-image en CI.
