package com.blu3berry.kraft.config

/**
 * Declares a property rename on a [@MapConfig][MapConfig] object.
 *
 * Tells Kraft to read [source] from the source class and write it to the [target]
 * constructor parameter on the target class.
 *
 * Write it directly on the object, one annotation per rename (repeatable), or as an
 * entry of [@MapConfig.fieldMappings][MapConfig.fieldMappings]. Both forms behave the
 * same; giving the same [target] in both is a compile error (KRAFT-E066). On a class
 * without `@MapConfig` it is a compile error (KRAFT-E071).
 *
 * @param source The property name on the **source** class.
 * @param target The constructor parameter name on the **target** class.
 *
 * Example:
 * ```
 * @MapConfig(source = User::class, target = UserDto::class)
 * @FieldMapping(source = "userId", target = "id")
 * @FieldMapping(source = "mail", target = "email")
 * object UserMappingConfig
 *
 * // Also supported:
 * @MapConfig(
 *     source = User::class,
 *     target = UserDto::class,
 *     fieldMappings = [FieldMapping(source = "userId", target = "id")]
 * )
 * object UserMappingConfig
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class FieldMapping(val source: String, val target: String)
