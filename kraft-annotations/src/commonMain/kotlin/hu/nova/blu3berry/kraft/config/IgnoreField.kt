package hu.nova.blu3berry.kraft.config

/**
 * Declares that a target constructor parameter should be skipped during mapping.
 *
 * Used inside [@MapConfig.ignoredMappings][MapConfig.ignoredMappings].
 *
 * [name] is always the **target-side** constructor parameter name for the direction
 * being generated:
 * - [IgnoreDirection.FORWARD]: the `to`-class parameter name.
 * - [IgnoreDirection.REVERSE]: the `from`-class parameter name (future).
 * - [IgnoreDirection.BOTH]: the processor checks which target(s) declare the
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
 *         IgnoreField("internalNotes"),
 *         IgnoreField("auditLog", direction = IgnoreDirection.FORWARD),
 *     ]
 * )
 * object UserMapping
 * ```
 */
annotation class IgnoreField(
    val name: String,
    val direction: IgnoreDirection = IgnoreDirection.BOTH
)
