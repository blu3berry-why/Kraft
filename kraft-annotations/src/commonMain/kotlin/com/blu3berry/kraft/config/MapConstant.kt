package com.blu3berry.kraft.config

/**
 * Supplies the value of a target constructor parameter from a member of a
 * [@MapConfig][MapConfig] object.
 *
 * Put it on a property or a zero-parameter function declared directly in the
 * `@MapConfig` object. The value is ordinary Kotlin, so any literal or expression works;
 * the generated mapper reads it through the object (`currency = UserMapper.currency`).
 *
 * - A **property** is evaluated once, when the object is initialised. Use it for fixed
 *   values.
 * - A **function** is called on every mapping. Use it for values that must be fresh each
 *   time, such as timestamps or generated IDs.
 *
 * The member must not be `private` (the generated mapper must read it), and a function
 * must take no parameters and must not be an extension. To compute a value from the
 * source object, use [@MapUsing][MapUsing] instead.
 *
 * @param target The constructor parameter name on the **target** class. Defaults to the
 *               annotated member's name.
 * @param direction Which mapping direction the constant applies to when the object also
 *                  carries [@MapReverse][MapReverse]. [ConverterDirection.AUTO] (the
 *                  default) applies it forward when the forward target has the parameter,
 *                  otherwise in reverse, never both. Use [ConverterDirection.FORWARD] or
 *                  [ConverterDirection.REVERSE] to choose explicitly.
 *
 * Example:
 * ```
 * @MapConfig(source = UserDto::class, target = User::class)
 * object UserMapper {
 *     @MapConstant val currency = "HUF"            // fills `currency`, evaluated once
 *
 *     @MapConstant(target = "createdAt")
 *     fun now() = Clock.System.now()               // called on every mapping
 * }
 * ```
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class MapConstant(
    val target: String = "",
    val direction: ConverterDirection = ConverterDirection.AUTO,
)
