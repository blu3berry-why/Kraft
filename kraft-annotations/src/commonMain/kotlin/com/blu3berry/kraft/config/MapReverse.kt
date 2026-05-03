package com.blu3berry.kraft.config

/**
 * Requests generation of a reverse mapper alongside the forward mapper.
 *
 * Place this annotation on a class annotated with
 * [@MapFrom][com.blu3berry.kraft.mapping.MapFrom] /
 * [@MapTo][com.blu3berry.kraft.mapping.MapTo], or on an object annotated with
 * [@MapConfig][MapConfig] or [@MapEnum][MapEnum], to generate the inverse
 * extension function automatically.
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
 * When both source and target share a property name with different types, Kraft
 * auto-detects converter direction by matching parameter types. Use
 * [ConverterDirection.FORWARD] or [ConverterDirection.REVERSE] on `@MapUsing`
 * to disambiguate when auto-detection is insufficient.
 *
 * Nested child mappers are auto-reversed unless an explicit reverse mapping already
 * exists for the child pair.
 *
 * **Reverse for `@MapEnum`:** explicit `FieldMapping` entries are inverted
 * (`source` ↔ `target`); the same auto-by-same-name fallback that `@MapEnum` uses
 * for the forward direction fills in the rest. Reverse-source entries with no
 * inverse produce a compile-time error — for example, when two forward sources
 * map to the same target, the reverse cannot disambiguate without a per-direction
 * configuration. Resolve by removing the conflicting forward mapping or moving to
 * two separate `@MapEnum` declarations.
 *
 * `@MapReverse` without a mapping annotation (`@MapFrom`, `@MapTo`, `@MapConfig`,
 * or `@MapEnum`) on the same declaration is a compile-time error.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class MapReverse
