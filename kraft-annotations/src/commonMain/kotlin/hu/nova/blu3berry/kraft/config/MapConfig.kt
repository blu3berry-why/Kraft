package hu.nova.blu3berry.kraft.config

import kotlin.reflect.KClass

/**
 * Marks an object as a mapping configuration from one class to another.
 *
 * Example:
 * @MapConfig(source = UserDto::class, target = User::class)
 * object UserMapping { ... }
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class MapConfig(
    val source: KClass<*>,
    val target: KClass<*>,
    val fieldMappings: Array<FieldMapping> = [],
    val nestedMappings: Array<NestedMapping> = [],
    val ignoredMappings: Array<MapIgnoreField> = [],
)
