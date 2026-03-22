package hu.nova.blu3berry.kraft.config

/**
 * Registers a custom converter function inside a [@MapConfig][MapConfig]-annotated object.
 *
 * The annotated function is called by the generated mapper to convert the value of
 * the [source] property into the value assigned to the [target] constructor parameter.
 * The function must be a member of the config object and have a signature compatible
 * with the source and target property types.
 *
 * @param source The property name on the **source** class whose value is passed in.
 * @param target The constructor parameter name on the **target** class that receives
 *               the converted value.
 *
 * Example:
 * ```
 * @MapConfig(source = User::class, target = UserDto::class)
 * object UserMappingConfig {
 *     @MapUsing(source = "birthDate", target = "age")
 *     fun birthDateToAge(birthDate: LocalDate): Int =
 *         Period.between(birthDate, LocalDate.now()).years
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapUsing(
    val source: String,
    val target: String
)
