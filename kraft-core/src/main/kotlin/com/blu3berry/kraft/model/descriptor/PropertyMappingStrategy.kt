package com.blu3berry.kraft.model.descriptor

import com.blu3berry.kraft.model.PropertyInfo

/**
 * Describes how a single target property should be populated in the generated mapper.
 *
 * Each variant is produced by a corresponding `MappingRule` during property resolution
 * and consumed by the code generators during code generation.
 *
 * @property targetProperty  The target property this strategy applies to.
 */
sealed interface PropertyMappingStrategy {
    val targetProperty: PropertyInfo

    /**
     * Direct "same name, same type" assignment:
     * `target.x = source.x`
     */
    data class Direct(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo
    ) : PropertyMappingStrategy

    /**
     * Renamed property, same type:
     * `target.newName = source.oldName`
     */
    data class Renamed(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo
    ) : PropertyMappingStrategy

    /**
     * Use a `@MapUsing` converter function:
     * - Property source: `source.prop.converter()` (extension) or `ConfigObject.converter(source.prop)` (object)
     * - Whole-source: `this.converter()` (extension) or `ConfigObject.converter(this)` (object)
     */
    data class ConverterFunction(
        override val targetProperty: PropertyInfo,
        val source: ConverterSource,
        val converter: ConverterDescriptor
    ) : PropertyMappingStrategy

    /**
     * Delegates to another generated mapper for a nested type:
     * `target.child = source.child.toChildDto()`
     */
    data class NestedMapper(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val nestedMappingDescriptor: NestedMappingDescriptor
    ) : PropertyMappingStrategy

    /**
     * Use a literal or expression as a constant value:
     * `target.isActive = true`, `target.id = "prefix-${source.id}"`
     *
     * Reserved for future use.
     */
    data class Constant(
        override val targetProperty: PropertyInfo,
        val expression: String
    ) : PropertyMappingStrategy

    /**
     * Target property is ignored — left to its default value.
     * No argument is emitted for this property in the constructor call.
     */
    data class Ignored(
        override val targetProperty: PropertyInfo
    ) : PropertyMappingStrategy
}
