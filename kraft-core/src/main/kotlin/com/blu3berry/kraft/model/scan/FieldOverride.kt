package com.blu3berry.kraft.model.scan

/**
 * A rename pair from a `@FieldMapping` declaration inside a `@MapConfig` object.
 *
 * @param source  The source property name.
 * @param target  The target property name.
 */
data class FieldOverride(
    val source: String,
    val target: String
)
