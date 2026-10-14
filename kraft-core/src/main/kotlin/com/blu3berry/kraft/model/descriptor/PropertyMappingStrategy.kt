package com.blu3berry.kraft.model.descriptor

import com.blu3berry.kraft.model.PropertyInfo
import com.blu3berry.kraft.model.TypeInfo

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
     *
     * @property shadowedNative The strategy Kraft would have used for this property had the
     *                          property-source `@MapUsing` not claimed it, or null when Kraft
     *                          has no native mapping for it. Only used to report `KRAFT-W001`;
     *                          it is never generated. Internal to `kraft-core` / `kraft-ksp`.
     */
    data class ConverterFunction(
        override val targetProperty: PropertyInfo,
        val source: ConverterSource,
        val converter: ConverterDescriptor,
        val shadowedNative: PropertyMappingStrategy? = null
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
     * Wraps the source value into a value class:
     * `target.id = UserId(source.id)`, `source.id?.let { UserId(it) }`,
     * or element-wise `source.ids.map { UserId(it) }` when [collectionKind] is set.
     *
     * @property valueClass     The value class constructed (the element type for collections).
     * @property safeCall       Whether the source (or the source collection) is nullable and must be safe-called.
     * @property collectionKind Non-null when the wrap applies to each element of a `List` / `Set`.
     */
    data class ValueClassWrap(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val valueClass: TypeInfo,
        val safeCall: Boolean,
        val collectionKind: CollectionKind? = null
    ) : PropertyMappingStrategy

    /**
     * Unwraps a value class to its underlying property:
     * `target.id = source.id.value`, `source.id?.value`,
     * or element-wise `source.ids.map { it.value }` when [collectionKind] is set.
     *
     * @property underlyingPropertyName The value class's single public property.
     * @property safeCall               Whether the source (or the source collection) is nullable.
     * @property collectionKind         Non-null when the unwrap applies to each element of a `List` / `Set`.
     */
    data class ValueClassUnwrap(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val underlyingPropertyName: String,
        val safeCall: Boolean,
        val collectionKind: CollectionKind? = null
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
