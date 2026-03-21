package hu.nova.blu3berry.kraft.config

/**
 * Declares that a target constructor parameter should be skipped during mapping.
 *
 * Used inside [@MapConfig.ignoredMappings][MapConfig.ignoredMappings].
 *
 * [name] is always the **target-side** constructor parameter name for the direction
 * being generated:
 * - [IgnoreSide.TARGET]: the `to`-class parameter name.
 * - [IgnoreSide.SOURCE]: the `from`-class parameter name (future).
 * - [IgnoreSide.BOTH]: the processor checks which target(s) declare the
 *   property and applies it where it exists.
 *
 * The skipped property must have a default value, otherwise the generated code
 * will not compile.
 *
 * Example:
 * ```
 * @MapConfig(
 *     from = User::class,
 *     to = UserDto::class,
 *     ignoredMappings = [
 *         MapIgnoreField("internalNotes"),
 *         MapIgnoreField("auditLog", direction = IgnoreSide.TARGET),
 *     ]
 * )
 * object UserMapping
 * ```
 */
annotation class MapIgnoreField(
    val name: String,
    val direction: IgnoreSide = IgnoreSide.BOTH
)
