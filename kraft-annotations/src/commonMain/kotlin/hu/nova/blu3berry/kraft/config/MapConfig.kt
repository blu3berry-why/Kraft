package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * Marks an object as a mapping configuration from one class to another.
 *
 * Example:
 * @MapConfig(from = UserDto::class, to = User::class)
 * object UserMapping { ... }
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapConfig(
    val from: KClass<*>,
    val to: KClass<*>,
    val fieldMapping: Array<StringPair> = []
)