package hu.nova.blu3berry.kraft.model.descriptor

import hu.nova.blu3berry.kraft.model.PropertyInfo
import hu.nova.blu3berry.kraft.model.TypeInfo

/**
 * Describes what value is passed into a [@MapUsing][hu.nova.blu3berry.kraft.config.MapUsing]
 * converter function.
 */
sealed interface ConverterSource {
    /** Converter receives the value of a single source property. */
    data class Property(val info: PropertyInfo) : ConverterSource

    /** Converter receives the entire source object. */
    data class WholeObject(val sourceType: TypeInfo) : ConverterSource
}
