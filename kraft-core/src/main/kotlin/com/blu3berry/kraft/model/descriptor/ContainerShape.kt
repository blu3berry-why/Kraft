package com.blu3berry.kraft.model.descriptor

import com.blu3berry.kraft.model.TypeInfo

/**
 * The container types Kraft recognises, keyed by the qualified name of their declaration.
 * Recognising a kind does not mean every pair of kinds is mapped: `ContainerRule` decides
 * which kinds and conversions it accepts.
 *
 * @property qualifiedName The declaration's qualified name (`kotlin.collections.List`).
 * @property isKeyed       True for `Map` / `MutableMap` (two type arguments: key and value).
 */
enum class ContainerKind(val qualifiedName: String, val isKeyed: Boolean = false) {
    LIST("kotlin.collections.List"),
    SET("kotlin.collections.Set"),
    COLLECTION("kotlin.collections.Collection"),
    ITERABLE("kotlin.collections.Iterable"),
    ARRAY("kotlin.Array"),
    MUTABLE_LIST("kotlin.collections.MutableList"),
    MUTABLE_SET("kotlin.collections.MutableSet"),
    MUTABLE_COLLECTION("kotlin.collections.MutableCollection"),
    MAP("kotlin.collections.Map", isKeyed = true),
    MUTABLE_MAP("kotlin.collections.MutableMap", isKeyed = true);

    /** The declaration's simple name (`List`), as shown in diagnostics. */
    val simpleName: String get() = qualifiedName.substringAfterLast('.')

    companion object {
        private val byQualifiedName = entries.associateBy { it.qualifiedName }

        /** The kind whose declaration is [qualifiedName], or null when it is not a container. */
        fun of(qualifiedName: String?): ContainerKind? = qualifiedName?.let { byQualifiedName[it] }
    }
}

/**
 * How a container-typed property is mapped, as a tree that mirrors the type: a [Linear]
 * node per single-element container level, a [Keyed] node per map level, and a [Leaf]
 * holding the operation applied to each element (or key / value).
 *
 * Every node records the nullability of its own source and target type, so the generator
 * can safe-call, fall back to an empty container, or drop null elements at each level.
 *
 * @property sourceNullable Whether the source type at this level is nullable.
 * @property targetNullable Whether the target type at this level is nullable.
 */
sealed interface ContainerShape {
    val sourceNullable: Boolean
    val targetNullable: Boolean

    /** One element (or key / value), mapped by [op]. */
    data class Leaf(
        val op: ElementOp,
        override val sourceNullable: Boolean,
        override val targetNullable: Boolean
    ) : ContainerShape

    /** A single-element container level (`List`, `Set`, `Array`, …) whose elements map by [element]. */
    data class Linear(
        val sourceKind: ContainerKind,
        val targetKind: ContainerKind,
        val element: ContainerShape,
        override val sourceNullable: Boolean,
        override val targetNullable: Boolean
    ) : ContainerShape

    /** A map level; keys map by [key] (never a container) and values by [value]. */
    data class Keyed(
        val sourceKind: ContainerKind,
        val targetKind: ContainerKind,
        val key: Leaf,
        val value: ContainerShape,
        override val sourceNullable: Boolean,
        override val targetNullable: Boolean
    ) : ContainerShape
}

/** The operation applied to one element, key or value of a container. */
sealed interface ElementOp {
    /** The element is kept as is. */
    data object Identity : ElementOp

    /** The element is mapped by a nested mapper (or an enum mapper, whose call shape is the same). */
    data class Nested(val descriptor: NestedMappingDescriptor) : ElementOp

    /** The element is passed to a converter function. */
    data class Converter(val converter: ConverterDescriptor) : ElementOp

    /** The element is wrapped into [valueClass]: `UserId(it)`. */
    data class Wrap(val valueClass: TypeInfo) : ElementOp

    /** The element is a value class unwrapped to [propertyName]: `it.value`. */
    data class Unwrap(val propertyName: String) : ElementOp
}

/** Every nested-mapper descriptor the shape's leaves call, in tree order (keys before values). */
fun ContainerShape.nestedDescriptors(): List<NestedMappingDescriptor> = when (this) {
    is ContainerShape.Leaf -> listOfNotNull((op as? ElementOp.Nested)?.descriptor)
    is ContainerShape.Linear -> element.nestedDescriptors()
    is ContainerShape.Keyed -> key.nestedDescriptors() + value.nestedDescriptors()
}
