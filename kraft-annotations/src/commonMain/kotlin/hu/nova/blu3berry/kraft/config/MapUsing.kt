package hu.nova.blu3berry.kraft.config

/**
 * Registers a custom converter function inside a [@MapConfig][MapConfig]-annotated object.
 *
 * The annotated function is called by the generated mapper to produce the value assigned
 * to the [target] constructor parameter.
 *
 * **Property-source mode** (`source` is specified): the function receives the value of
 * the named source property and converts it to the target type.
 *
 * **Whole-source mode** (`source` is omitted or blank): the function receives the entire
 * source object, allowing multiple source properties to be combined into one target value.
 * The function parameter (or extension receiver) must match the source class type.
 *
 * @param source The property name on the **source** class whose value is passed in.
 *               Omit (or leave blank) to pass the whole source object instead.
 * @param target The constructor parameter name on the **target** class that receives
 *               the converted value.
 *
 * Examples:
 * ```
 * // Property-source: convert one field
 * @MapUsing(source = "birthDate", target = "age")
 * fun birthDateToAge(birthDate: LocalDate): Int =
 *     Period.between(birthDate, LocalDate.now()).years
 *
 * // Whole-source: combine multiple fields
 * @MapUsing(target = "fullName")
 * fun User.toFullName(): String = "$firstName $lastName"
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapUsing(
    val source: String = "",
    val target: String
)
