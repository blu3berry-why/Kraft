package hu.nova.blu3berry.kraft.mapping

import kotlin.reflect.KClass

/**
 * Marks this class as the **source** of a mapping to [target].
 *
 * The annotated class is the source; Kraft generates an extension function on it
 * that constructs an instance of [target]. [target] must have a primary constructor.
 *
 * Property matching is done by name. Use [@MapField][MapField] on a property to
 * rename the target-side lookup, [@MapNested][MapNested] to trigger nested object
 * mapping, and [@MapIgnore][MapIgnore] to skip a property entirely.
 *
 * @param target The class to map **to**.
 * @param config Optional config object class annotated with [@MapConfig][hu.nova.blu3berry.kraft.config.MapConfig]
 *               that provides field mappings, nested mappings, converters, and ignore rules.
 *               Defaults to [Unit] (no config).
 *
 * Example:
 * ```
 * data class UserDto(val id: Int, val fullName: String)
 *
 * @MapTo(UserDto::class)
 * data class User(val id: Int, val fullName: String)
 * ```
 *
 * With a config object:
 * ```
 * @MapTo(UserDto::class, config = UserMappingConfig::class)
 * data class User(val id: Int, val name: String)
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapTo(
    val target: KClass<*>,
    val config: KClass<*> = Unit::class
)
