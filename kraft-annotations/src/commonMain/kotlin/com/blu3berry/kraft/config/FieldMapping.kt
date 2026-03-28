package com.blu3berry.kraft.config

/**
 * Declares a property rename inside [@MapConfig.fieldMappings][MapConfig.fieldMappings].
 *
 * Tells Kraft to read [source] from the source class and write it to the [target]
 * constructor parameter on the target class.
 *
 * @param source The property name on the **source** class.
 * @param target The constructor parameter name on the **target** class.
 *
 * Example:
 * ```
 * @MapConfig(
 *     source = User::class,
 *     target = UserDto::class,
 *     fieldMappings = [FieldMapping(source = "userId", target = "id")]
 * )
 * object UserMappingConfig
 * ```
 */
annotation class FieldMapping(val source: String, val target: String)
