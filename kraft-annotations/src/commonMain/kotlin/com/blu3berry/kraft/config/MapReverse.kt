package com.blu3berry.kraft.config

/**
 * Requests generation of a reverse mapper alongside the forward mapper.
 *
 * Place this annotation on a class annotated with
 * [@MapFrom][com.blu3berry.kraft.mapping.MapFrom] /
 * [@MapTo][com.blu3berry.kraft.mapping.MapTo], or on an object annotated with
 * [@MapConfig][MapConfig], to generate the inverse extension function automatically.
 *
 * **Example — class-level:**
 * ```kotlin
 * @MapReverse
 * @MapFrom(User::class)
 * data class UserDto(val id: Int, val name: String)
 *
 * // Generates both:
 * //   fun User.toUserDto(): UserDto
 * //   fun UserDto.toUser(): User
 * ```
 *
 * **Example — config-level:**
 * ```kotlin
 * @MapReverse
 * @MapConfig(source = User::class, target = UserDto::class)
 * object UserMapping
 *
 * // Generates both directions.
 * ```
 *
 * If the forward mapping uses [@MapUsing][MapUsing] converters, the reverse direction
 * requires a corresponding reverse converter to be defined in the same config object.
 * A compile-time error is emitted when a reverse converter is missing.
 *
 * Nested child mappers are auto-reversed unless an explicit reverse mapping already
 * exists for the child pair.
 *
 * `@MapReverse` without a mapping annotation (`@MapFrom`, `@MapTo`, or `@MapConfig`)
 * on the same declaration is a compile-time error.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapReverse
