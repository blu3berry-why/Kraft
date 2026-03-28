package com.blu3berry.kraft.mapping

/**
 * Declares that this property maps to/from a differently named property on the
 * counterpart class.
 *
 * [counterPartName] always refers to the property name on the **other** class:
 * - On a [@MapFrom][MapFrom] class (annotated class = target): `counterPartName` is
 *   the **source** property name.
 * - On a [@MapTo][MapTo] class (annotated class = source): `counterPartName` is
 *   the **target** property name.
 *
 * @param counterPartName The property name on the counterpart class.
 *
 * Example on `@MapFrom` (source property has a different name):
 * ```
 * data class User(val userId: Int, val fullName: String)
 *
 * @MapFrom(User::class)
 * data class UserDto(
 *     @MapField(counterPartName = "userId")
 *     val id: Int,
 *     val fullName: String
 * )
 * ```
 *
 * Example on `@MapTo` (target property has a different name):
 * ```
 * data class UserDto(val id: Int, val fullName: String)
 *
 * @MapTo(UserDto::class)
 * data class User(
 *     @MapField(counterPartName = "id")
 *     val userId: Int,
 *     val fullName: String
 * )
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapField(
    val counterPartName: String
)
