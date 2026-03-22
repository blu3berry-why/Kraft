package hu.nova.blu3berry.kraft.mapping

import kotlin.reflect.KClass

/**
 * Marks this class as the **target** of a mapping from [source].
 *
 * The annotated class must have a primary constructor; Kraft generates an
 * extension function on [source] that constructs an instance of the annotated class.
 *
 * Property matching is done by name. Use [@MapField][MapField] on a property to
 * rename the source-side lookup, [@MapNested][MapNested] to trigger nested object
 * mapping, and [@MapIgnore][MapIgnore] to skip a property entirely.
 *
 * @param source The class to map **from**.
 * @param config Optional config object class annotated with [@MapConfig][hu.nova.blu3berry.kraft.config.MapConfig]
 *               that provides field mappings, nested mappings, converters, and ignore rules.
 *               Defaults to [Unit] (no config).
 *
 * Example:
 * ```
 * data class User(val id: Int, val fullName: String)
 *
 * @MapFrom(User::class)
 * data class UserDto(val id: Int, val fullName: String)
 * ```
 *
 * With a config object:
 * ```
 * @MapFrom(User::class, config = UserMappingConfig::class)
 * data class UserDto(val id: Int, val name: String)
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapFrom(
    val source: KClass<*>,
    val config: KClass<*> = Unit::class
)
