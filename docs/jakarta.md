# KValid frente a Jakarta Validation

KValid toma prestado de Jakarta el **vocabulario** —los nombres y la semántica de los
constraints— pero **no implementa la especificación**, y tampoco lee sus anotaciones. Esta
página dice exactamente qué coincide, qué no, y por qué.

!!! info "En una frase"
    Si vienes de Hibernate Validator, tus anotaciones se llaman igual y hacen lo mismo. Lo que
    cambia es *cuándo* se resuelven (compilación, no runtime) y *qué* no está (`groups`).

---

## 1 · Por qué no se implementa la especificación

No es una cuestión de esfuerzo: son dos incompatibilidades de diseño.

**`jakarta.validation-api` es un JAR de JVM.** Publica un único artefacto Java: no hay klib ni
variantes de Kotlin/Native. Si los constraints de KValid fueran `jakarta.validation.constraints.*`,
`commonMain` tendría que depender de él y **se acabaría el soporte multiplataforma** — que es
justamente la razón de ser de la librería.

**La SPI de la especificación está moldeada alrededor de la reflexión.** `Validator.validate(Object)`
recibe `Object`; `ConstraintValidator` se instancia por una factoría; y la API de metadatos
(`getConstraintsForClass`, `BeanDescriptor`) es introspección pura. Implementarla de verdad
significa implementar reflexión, que es exactamente lo que KValid evita.

Una conformidad parcial anunciada como total sería peor que no tenerla, así que no se anuncia.

## 2 · Por qué tampoco se leen las anotaciones `jakarta.validation` en Java

Sería tentador: los proyectos Java ya tienen sus DTOs anotados. Pero el resultado sería que
**la misma anotación significaría cosas distintas según quién la procesara**, que es el peor
fallo posible en una librería de validación. Los desajustes concretos:

| Detalle | Jakarta / Hibernate | KValid |
|---|---|---|
| `groups` y `payload` | En todas las anotaciones | No existen (ver §4) |
| Cascada | `@Valid` sobre el campo | Implícita: el tipo lleva `@Validated` |
| Mensajes | Plantillas EL `{clave}` + `ValidationMessages.properties` | `code` + `params`, resueltos por `kvalid-i18n` |
| `@Pattern` | Tiene `flags` | No los tiene |
| `@DecimalMin/@DecimalMax` | Tienen `inclusive` | Siempre inclusivos |
| `@Email` | Regex propia de Hibernate | Regex propia, más estricta en unos casos y más laxa en otros |

Leer la anotación e ignorar en silencio `groups`, `flags` o `inclusive` cambiaría el
comportamiento del proyecto sin avisar. Y respetarlos exigiría reimplementar media
especificación. Ninguna de las dos merece la pena, así que **KValid solo procesa sus propias
anotaciones**, y una clase con anotaciones de Jakarta simplemente no genera nada.

Puedes usar los dos a la vez: el starter de Spring compone su `Validator` con el de Hibernate
si está en el classpath, así que las clases con Jakarta las sigue validando Hibernate.

## 3 · Tabla de equivalencias

Los 22 constraints de Jakarta Validation 3.0:

| Jakarta | KValid | Notas |
|---|---|---|
| `@NotNull` | ✅ `@NotNull` | |
| `@Null` | ✅ `@Null` | Error de compilación si el tipo no admite null |
| `@NotBlank` | ✅ `@NotBlank` | |
| `@NotEmpty` | ✅ `@NotEmpty` | String o colección |
| `@Size` | ✅ `@Size` | |
| `@Pattern` | ⚠️ `@Pattern` | Sin `flags` |
| `@Email` | ⚠️ `@Email` | Regex distinta |
| `@Min` / `@Max` | ✅ `@Min` / `@Max` | |
| `@DecimalMin` / `@DecimalMax` | ⚠️ `@DecimalMin` / `@DecimalMax` | Sin `inclusive` (siempre inclusivos) |
| `@Digits` | ⚠️ `@Digits` | No admite `Double`/`Float` (ver §5) |
| `@Positive` / `@Negative` | ✅ `@Positive` / `@Negative` | |
| `@PositiveOrZero` / `@NegativeOrZero` | ✅ `@PositiveOrZero` / `@NegativeOrZero` | |
| `@AssertTrue` / `@AssertFalse` | ✅ `@AssertTrue` / `@AssertFalse` | |
| `@Past` / `@Future` | ✅ `@Past` / `@Future` | `Instant` (kotlinx o `java.time`) |
| `@PastOrPresent` / `@FutureOrPresent` | ✅ `@PastOrPresent` / `@FutureOrPresent` | idem |

**Además, fuera del estándar:** `@Range` y `@Url` (ambas existen en Hibernate como extensiones
de proveedor) y `@OneOf` (propia de KValid).

## 4 · `groups`: no está, y es a propósito

Es la diferencia que más se nota al migrar, así que conviene el razonamiento completo.

**Invierte el acoplamiento.** `groups = {OnCreate.class}` en un campo hace que el modelo sepa
qué casos de uso existen: el DTO pasa a depender de la operación, cuando debería ser al revés.
Y los grupos son interfaces marcadoras vacías, metadatos disfrazados de tipos.

**Y sobre todo: falla en silencio.** Si olvidas pasar el grupo, los constraints de ese grupo
**no se ejecutan** — y un constraint que no corre es indistinguible de uno que pasó. Ese es
justo el fallo que KValid existe para eliminar: no tiene sentido prometer que un error se
detecta al compilar y a la vez ofrecer una validación que desaparece según un parámetro de
runtime.

**La alternativa: un tipo por caso de uso.**

```kotlin
// En vez de una clase con grupos…
@Validated
data class CreateUser(
    @NotBlank @Size(max = 40) val name: String,
    @Email val email: String,
)

@Validated
data class UpdateUser(
    @NotNull val id: Long?,
    @Size(max = 40) val name: String?,   // opcional al actualizar
)
```

Cada endpoint recibe el tipo que le corresponde, el compilador te impide mezclarlos y no hay
ningún parámetro que se pueda olvidar. En Kotlin una data class cuesta tres líneas.

`payload` tampoco existe, por lo mismo: su uso habitual —clasificar la severidad— se cubre
mejor con un `code` propio y un `@Constraint` a medida.

## 5 · Diferencias de comportamiento que conviene conocer

### `@Digits` no admite `Double` ni `Float`

Igual que la especificación, que solo lo define para enteros, `BigDecimal`, `BigInteger` y
`CharSequence`: el binario de coma flotante no tiene un número exacto de dígitos decimales, así
que contarlos daría resultados dependientes del redondeo. KValid lo rechaza **en compilación**
con un mensaje que lo explica, en vez de dar un resultado dudoso en runtime.

### `@Digits` cuenta dígitos enteros *significativos*

`0.5` tiene **0** dígitos enteros (Hibernate, que cuenta `precision - scale`, da lo mismo).
La única divergencia es el valor entero `0`: KValid cuenta 0 dígitos y Hibernate 1, así que
`@Digits(integer = 0, ...)` los trata distinto. Es un caso degenerado, pero queda dicho.

Los ceros a la derecha de los decimales **sí** cuentan (`1.50` son dos decimales), como en
Jakarta.

### `@DecimalMin`/`@DecimalMax` comparan según el tipo

A diferencia de Hibernate, que convierte todo a `BigDecimal` en runtime, KValid elige la
comparación al generar:

| Tipo de la propiedad | Se emite | Por qué |
|---|---|---|
| `Int` `Long` `Short` `Byte` | `v.toLong() < 1L` | La cota se redondea **en compilación**: para un entero, `v < 0.5` es exactamente `v < 1` |
| `Double` `Float` | `v.toDouble() < 0.01` | Comparar un valor aproximado con precisión exacta sería falsa exactitud |
| `BigDecimal` `BigInteger` | `v.compareTo(DEC_0_01) < 0` | Exacto; la cota se construye una vez por archivo |

Consecuencia práctica: solo se emite `BigDecimal` cuando la propiedad **ya es** de un tipo que
únicamente existe en la JVM, así que estos constraints funcionan en `commonMain` con targets
nativos, JS y Wasm. Y no se asigna ningún objeto por validación.

La comparación con `BigDecimal` usa `compareTo`, no `equals`, así que ignora la escala:
`1.00` y `1.0` son el mismo valor.

### Los mensajes no son plantillas EL

Jakarta interpola `{jakarta.validation.constraints.Size.message}` con un motor de expresiones.
KValid emite un `code` estable (`"size.max"`) y un mapa de `params` (`{"max": 40}`), y la
traducción es cosa de `kvalid-i18n` o de tu propia capa. Es lo que permite que el mismo
`ValidationResult` se renderice en el servidor y en el cliente, cada uno en su idioma.

## 6 · Migrar desde Hibernate Validator

1. Cambia los imports de `jakarta.validation.constraints.*` a `dev.kvalid.annotations.*`.
2. Añade `@Validated` a las clases (Jakarta no lo necesita porque descubre por reflexión).
3. Sustituye `@Valid` sobre campos anidados por `@Validated` en el **tipo** anidado.
4. Si usabas `groups`, parte la clase en un tipo por caso de uso (§4).
5. Compila. Lo que no encaje será un error con su explicación, no un fallo en runtime.

En Spring no hace falta tocar los controllers: `@Valid` sigue funcionando igual — ver
[Spring Boot](spring.md).
