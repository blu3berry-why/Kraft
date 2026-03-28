package com.blu3berry.kraft.mapping

/**
 * Excludes this property from mapping. The corresponding target constructor parameter
 * is omitted from the generated call, so it **must** declare a default value —
 * otherwise the generated code will not compile.
 *
 * Behaviour differs by annotation direction:
 * - On a [@MapFrom][MapFrom] class (annotated class = target): the annotated property
 *   is a target constructor parameter and is skipped directly.
 * - On a [@MapTo][MapTo] class (annotated class = source): the annotated property name
 *   is matched against the target constructor by name. This only works when both
 *   classes share the same property name. Use [@MapIgnoreField][com.blu3berry.kraft.config.MapIgnoreField]
 *   inside a [@MapConfig][com.blu3berry.kraft.config.MapConfig] for cross-name ignores.
 *
 * Example:
 * ```
 * data class User(val name: String, val internalNotes: String)
 *
 * @MapFrom(User::class)
 * data class UserDto(
 *     val name: String,
 *     @MapIgnore
 *     val internalNotes: String = ""   // must have a default value
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapIgnore
