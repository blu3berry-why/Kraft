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
     * `target.child = source.child.toChildDto()`.
     * Element-wise nested mapping is a [ContainerMapping] with an [ElementOp.Nested] leaf.
     */
    data class NestedMapper(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val nestedMappingDescriptor: NestedMappingDescriptor
    ) : PropertyMappingStrategy

    /**
     * Wraps the source value into a value class:
     * `target.id = UserId(source.id)` or `source.id?.let { UserId(it) }`.
     * Element-wise wraps are [ContainerMapping]s with an [ElementOp.Wrap] leaf.
     *
     * @property valueClass     The value class constructed.
     * @property safeCall       Whether the source is nullable and must be safe-called.
     */
    data class ValueClassWrap(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val valueClass: TypeInfo,
        val safeCall: Boolean
    ) : PropertyMappingStrategy

    /**
     * Unwraps a value class to its underlying property:
     * `target.id = source.id.value` or `source.id?.value`.
     * Element-wise unwraps are [ContainerMapping]s with an [ElementOp.Unwrap] leaf.
     *
     * @property underlyingPropertyName The value class's single public property.
     * @property safeCall               Whether the source is nullable.
     */
    data class ValueClassUnwrap(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val underlyingPropertyName: String,
        val safeCall: Boolean
    ) : PropertyMappingStrategy

    /**
     * Maps a container-typed property level by level, as described by [shape]:
     * `target.items = source.items.map { it.toItemDto() }`,
     * `source.ids?.map { UserId(it) }?.toSet()`, etc.
     *
     * @property shape The container tree, from the property's own type down to the element operations.
     */
    data class ContainerMapping(
        override val targetProperty: PropertyInfo,
        val sourceProperty: PropertyInfo,
        val shape: ContainerShape
    ) : PropertyMappingStrategy

    /**
     * Use a literal or expression as a constant value:
     * `target.isActive = true`, `target.id = "prefix-${source.id}"`
     *
     * Produced by `PropertyResolver` with `expression = "null"` for an unmapped nullable
     * target property that has no default value.
     */
    data class Constant(
        override val targetProperty: PropertyInfo,
        val expression: String
    ) : PropertyMappingStrategy

    /**
     * Reads a `@MapConstant` member of the `@MapConfig` object:
     * `target.currency = UserMapper.currency` (property) or
     * `target.createdAt = UserMapper.now()` (function, called on every mapping).
     */
    data class ConstantMember(
        override val targetProperty: PropertyInfo,
        val constant: ConstantDescriptor
    ) : PropertyMappingStrategy

    /**
     * Target property is ignored — left to its default value.
     * No argument is emitted for this property in the constructor call.
     */
    data class Ignored(
        override val targetProperty: PropertyInfo
    ) : PropertyMappingStrategy
}

/**
 * The nested-mapper descriptors this strategy calls: the one of a [PropertyMappingStrategy.NestedMapper],
 * the leaves of a [PropertyMappingStrategy.ContainerMapping], and none for every other strategy.
 */
fun PropertyMappingStrategy.nestedDescriptors(): List<NestedMappingDescriptor> = when (this) {
    is PropertyMappingStrategy.NestedMapper -> listOf(nestedMappingDescriptor)
    is PropertyMappingStrategy.ContainerMapping -> shape.nestedDescriptors()
    is PropertyMappingStrategy.ConstantMember -> emptyList()
    else -> emptyList()
}
