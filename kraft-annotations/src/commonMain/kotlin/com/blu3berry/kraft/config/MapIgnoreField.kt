package com.blu3berry.kraft.config

/**
 * Declares that a target constructor parameter should be skipped during mapping.
 *
 * Write it directly on a [@MapConfig][MapConfig] object, one annotation per property
 * (repeatable), or as an entry of [@MapConfig.ignoredMappings][MapConfig.ignoredMappings].
 * Both forms behave the same; giving the same [name] in both is a compile error
 * (KRAFT-E066). On a class without `@MapConfig` it is a compile error (KRAFT-E071).
 *
 * [name] is always the **target-side** constructor parameter name for the direction
 * being generated:
 * - [IgnoreSide.TARGET]: the `target`-class parameter name.
 * - [IgnoreSide.SOURCE]: the `source`-class parameter name (future).
 * - [IgnoreSide.BOTH]: the processor checks which target(s) declare the
 *   property and applies it where it exists.
 *
 * The skipped property must have a default value, otherwise the generated code
 * will not compile.
 *
 * Example:
 * ```
 * @MapConfig(source = User::class, target = UserDto::class)
 * @MapIgnoreField("internalNotes")
 * @MapIgnoreField("auditLog", direction = IgnoreSide.TARGET)
 * object UserMapping
 *
 * // Also supported:
 * @MapConfig(
 *     source = User::class,
 *     target = UserDto::class,
 *     ignoredMappings = [MapIgnoreField("internalNotes")]
 * )
 * object UserMapping
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapIgnoreField(
    val name: String,
    val direction: IgnoreSide = IgnoreSide.BOTH
)
