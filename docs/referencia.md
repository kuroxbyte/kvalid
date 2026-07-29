# KValid — guía de referencia

Referencia por temas del comportamiento y del código generado. Para la visión general, ver
el [README](https://github.com/kuroxbyte/kvalid).

## La función generada

Por cada clase `@Validated` se genera `<Simple>Validator.kt` en su paquete, con:

```kotlin
public fun User.validate(): ValidationResult<User>
```

Acumula todas las violaciones y devuelve `Valid(this)` si no hubo ninguna, o
`Invalid(violations)` si las hubo. Las regex de `@Pattern`/`@Email` se declaran a nivel de
archivo (se compilan una vez):

```kotlin
private val EMAIL_REGEX: Regex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val code_pattern: Regex = Regex("[A-Z]{3}")   // por cada @Pattern
```

## Emisión por constraint

Sobre el valor presente `v` (para propiedades no-nullable, `this.f.let { v -> ... }`; para
nullable, `this.f?.let { v -> ... }`):

| Constraint         | Check generado                                                                 |
|--------------------|--------------------------------------------------------------------------------|
| `@NotBlank`        | `if (v.isBlank()) violations += Violation("f", "notBlank")`                     |
| `@NotEmpty`        | `if (v.isEmpty()) violations += Violation("f", "notEmpty")`                     |
| `@Size(min,max)`   | `if (v.length < min) ... "size.min"` y `if (v.length > max) ... "size.max"` (String) / `v.size` (colección) |
| `@Pattern`         | `if (!f_pattern.matches(v)) violations += Violation("f", "pattern")`            |
| `@Email`           | `if (!EMAIL_REGEX.matches(v)) violations += Violation("f", "email")`            |
| `@Min(value)`      | comparación en el tipo propio (ver abajo) → `"min"` (params: min)                |
| `@Max(value)`      | comparación en el tipo propio → `"max"` (params: max)                            |
| `@Range(min,max)`  | `min` y `max` combinados → `"range"` (params: min,max)                           |
| `@DecimalMin(v)`   | Según el tipo: entero → `v.toLong() < ⌈cota⌉`; `Double`/`Float` → `v.toDouble() < cota`; `BigDecimal`/`BigInteger` → `v.compareTo(DEC_x) < 0` con la cota izada a constante de archivo |
| `@DecimalMax(v)`   | Igual, con `>` y `⌊cota⌋`                                                        |
| `@Positive`        | `> 0` en el tipo propio → `"positive"`                                           |
| `@Negative`        | `< 0` en el tipo propio → `"negative"`                                           |
| `@PositiveOrZero`  | `>= 0` en el tipo propio → `"positiveOrZero"`                                    |
| `@NegativeOrZero`  | `<= 0` en el tipo propio → `"negativeOrZero"`                                    |
| `@AssertTrue`      | `if (!v) violations += Violation("f", "assertTrue")` (solo `Boolean`)            |
| `@AssertFalse`     | `if (v) violations += Violation("f", "assertFalse")` (solo `Boolean`)            |
| `@Digits(i,f)`     | `if (Digits.exceeds(v.toString(), i, f)) ... "digits"` (params: integer,fraction) |
| `@PastOrPresent`   | violación si el instante está en el futuro → `"pastOrPresent"`                   |
| `@FutureOrPresent` | violación si el instante está en el pasado → `"futureOrPresent"`                 |
| `@NotNull`         | `if (this.f == null) violations += Violation("f", "notNull")` (fuera del bloque de valor presente) |
| `@Null`            | `if (this.f != null) violations += Violation("f", "null")` (idem; error si el tipo no admite null) |

**Comparación numérica por tipo (sin pérdida de precisión):** NO se usa `toDouble`. Según el
tipo de la propiedad:

| Tipo                       | `@Min(x)` (violación si...)                     | `@Positive` (violación si...) |
|----------------------------|-------------------------------------------------|-------------------------------|
| `Int`/`Long`/`Short`/`Byte`| `v.toLong() < xL`                               | `v.toLong() <= 0L`            |
| `Double`/`Float`           | `v.toDouble() < x.0`                             | `v.toDouble() <= 0.0`         |
| `BigInteger`               | `v < BigInteger.valueOf(xL)`                     | `v.signum() <= 0`             |
| `BigDecimal`               | `v < BigDecimal.valueOf(xL)`                     | `v.signum() <= 0`             |

Así `@Min(9007199254740993L)` sobre un `Long` es exacto (con `toDouble` colapsaría con
`2^53`). Para límites decimales usa `@DecimalMin`/`@DecimalMax` (compara con scale-insensitive
`compareTo`).

## Nulabilidad

Una propiedad nullable se valida **solo si está presente** (`?.let`), salvo `@NotNull`, que
comprueba justamente la ausencia. Es la semántica esperada: "si hay valor, que cumpla".

## Cascada (`@Validated` anidado)

Si el tipo de una propiedad es `@Validated`, se valida en cascada y sus violaciones se
rebasan con el prefijo del campo:

```kotlin
when (val r = v.validate()) {
    is ValidationResult.Invalid -> violations += r.violations.map { Violation("address." + it.path, it.code, it.params) }
    else -> {}
}
```

Así una violación `street` del `Address` anidado aparece como `address.street`. El rebasing es
recursivo: `order.address.street` sale solo por composición.

## Mensajes

Cada constraint acepta `message` opcional. El generado lo pasa como argumento nombrado a la
`Violation` solo si está presente:

```kotlin
if (v.isBlank()) violations += Violation("name", "notBlank", message = "obligatorio")
```

Si no hay message, se emite `Violation("name", "notBlank")` y `message` queda `null`.

## Constraints reutilizables — `@Constraint` + `ConstraintValidator`

Una anotación meta-anotada con `@Constraint(validatedBy = V)` (donde `V` es un `object`
`ConstraintValidator<T>`) se resuelve en build-time y el generado invoca al validador
directamente. Se comparte un único `ValidationContext` (`val ctx`) al que empujan todos:

```kotlin
val ctx = ValidationContext()
this.handle.let { v -> SlugValidator.validate(v, "handle", ctx) }   // property-level
DateOkValidator.validate(this, "", ctx)                              // class-level (cross-field)
violations += ctx.violations
```

- **Property-level**: `T` es el tipo de la propiedad; se pasa el nombre del campo como `field`.
- **Class-level**: la anotación va en la clase, `T` es la data class, `field = ""`. Es el
  reemplazo type-safe de la vieja convención `validateCustom` — el compilador obliga la firma
  (implementas una interfaz) y el validador es reutilizable.

El validador es un `object` sin estado. Parametrizar constraints custom (leer los args de la
anotación en el validador) queda **diferido**.

## Detección de aplicabilidad

El builder rechaza en compilación un constraint sobre un tipo incompatible
(`kvalid.constraint.type`):

- texto (`@NotBlank`, `@Email`, `@Pattern`) → requiere `String`.
- `@NotEmpty`/`@Size` → `String` o colección (`List`/`Set`/`Map`/`Iterable`).
- numéricos (`@Min`/`@Max`/`@Range`/`@Positive`/`@Negative`) → `Int`/`Long`/`Short`/`Byte`/
  `Double`/`Float`/`BigDecimal`/`BigInteger`.
- `@NotNull` → cualquier tipo.

## Diagnósticos

| Código                    | Severidad | Cuándo                                                     |
|---------------------------|-----------|------------------------------------------------------------|
| `kvalid.constraint.type`  | error     | constraint sobre un tipo incompatible.                     |

## Códigos de violación

`notBlank`, `notEmpty`, `size.min`, `size.max`, `pattern`, `email`, `min`, `max`, `range`,
`positive`, `negative`, `notNull`, más los que emitan tus `ConstraintValidator`. La capa de
i18n (opcional) mapea `code` + `params` a texto; `message` es un override opcional por
constraint.

## Diferido

Constraints sobre elementos de colección (`List<@NotBlank String>`): requiere `@Target(TYPE)`
y leer anotaciones de argumento de tipo en KSP. Diferido (no bloquea el resto).
