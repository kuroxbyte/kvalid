# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es/1.1.0/).
Versionado semántico.

## [Sin publicar]

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
