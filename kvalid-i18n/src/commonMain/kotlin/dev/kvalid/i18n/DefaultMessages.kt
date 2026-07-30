package dev.kvalid.i18n

/**
 * Plantillas listas para usar, para no empezar viendo `"notBlank"` en las respuestas.
 *
 * ```
 * val resolver = DefaultMessageResolver(DefaultMessages.ES)
 * resolver.resolve(Violation("name", "size.max", mapOf("max" to 80)))  // "el tamaño debe ser como máximo 80"
 * ```
 *
 * Son mapas de Kotlin y no ficheros `.properties` porque esto vive en `commonMain`: fuera de la
 * JVM no existen `ResourceBundle` ni `Locale`. Por eso el idioma lo elige quien llama —con
 * [forLanguage] si tienes un tag de idioma a mano— en vez de resolverse por el locale del
 * sistema.
 *
 * Solo hay **es** e **in**glés a propósito: mantener veinte traducciones que no se pueden
 * revisar es una promesa que no conviene hacer. Para añadir un idioma, copia [EN], traduce los
 * valores y pásalo al resolutor; y para cambiar un texto suelto, [DefaultMessageResolver] ya
 * acepta un mapa combinado (`DefaultMessages.ES + mapOf("email" to "…")`).
 *
 * Las claves son los `code` que emite el generador, y [CODES] los lista todos: si añades un
 * constraint nuevo y olvidas su mensaje, el test de cobertura lo detecta.
 */
public object DefaultMessages {

    /** Todos los `code` que puede emitir el generador. */
    public val CODES: Set<String> = setOf(
        "notNull", "null", "notBlank", "notEmpty",
        "size.min", "size.max", "pattern", "email", "url", "oneOf",
        "min", "max", "range", "decimalMin", "decimalMax", "digits",
        "positive", "negative", "positiveOrZero", "negativeOrZero",
        "assertTrue", "assertFalse",
        "past", "future", "pastOrPresent", "futureOrPresent",
    )

    public val EN: Map<String, String> = mapOf(
        "notNull" to "must not be null",
        "null" to "must be null",
        "notBlank" to "must not be blank",
        "notEmpty" to "must not be empty",
        "size.min" to "size must be at least {min}",
        "size.max" to "size must be at most {max}",
        "pattern" to "does not match the required format",
        "email" to "must be a well-formed email address",
        "url" to "must be a well-formed URL",
        "oneOf" to "must be one of the allowed values",
        "min" to "must be greater than or equal to {min}",
        "max" to "must be less than or equal to {max}",
        "range" to "must be between {min} and {max}",
        "decimalMin" to "must be greater than or equal to {min}",
        "decimalMax" to "must be less than or equal to {max}",
        "digits" to "must have at most {integer} integer digits and {fraction} decimal digits",
        "positive" to "must be greater than 0",
        "negative" to "must be less than 0",
        "positiveOrZero" to "must be greater than or equal to 0",
        "negativeOrZero" to "must be less than or equal to 0",
        "assertTrue" to "must be true",
        "assertFalse" to "must be false",
        "past" to "must be a date in the past",
        "future" to "must be a date in the future",
        "pastOrPresent" to "must be a date in the past or the present",
        "futureOrPresent" to "must be a date in the present or the future",
    )

    public val ES: Map<String, String> = mapOf(
        "notNull" to "no puede ser nulo",
        "null" to "debe ser nulo",
        "notBlank" to "no puede estar en blanco",
        "notEmpty" to "no puede estar vacío",
        "size.min" to "el tamaño debe ser como mínimo {min}",
        "size.max" to "el tamaño debe ser como máximo {max}",
        "pattern" to "no cumple el formato requerido",
        "email" to "debe ser una dirección de correo válida",
        "url" to "debe ser una URL válida",
        "oneOf" to "debe ser uno de los valores permitidos",
        "min" to "debe ser mayor o igual que {min}",
        "max" to "debe ser menor o igual que {max}",
        "range" to "debe estar entre {min} y {max}",
        "decimalMin" to "debe ser mayor o igual que {min}",
        "decimalMax" to "debe ser menor o igual que {max}",
        "digits" to "debe tener como máximo {integer} dígitos enteros y {fraction} decimales",
        "positive" to "debe ser mayor que 0",
        "negative" to "debe ser menor que 0",
        "positiveOrZero" to "debe ser mayor o igual que 0",
        "negativeOrZero" to "debe ser menor o igual que 0",
        "assertTrue" to "debe ser verdadero",
        "assertFalse" to "debe ser falso",
        "past" to "debe ser una fecha en el pasado",
        "future" to "debe ser una fecha en el futuro",
        "pastOrPresent" to "debe ser una fecha en el pasado o el presente",
        "futureOrPresent" to "debe ser una fecha en el presente o el futuro",
    )

    /**
     * Plantillas para un tag de idioma (`"es"`, `"es-PE"`, `"en-US"`…). Cualquier idioma que no
     * sea español cae en [EN]: es preferible un mensaje entendible a devolver el `code` crudo.
     */
    public fun forLanguage(languageTag: String): Map<String, String> =
        if (languageTag.take(2).lowercase() == "es") ES else EN
}
