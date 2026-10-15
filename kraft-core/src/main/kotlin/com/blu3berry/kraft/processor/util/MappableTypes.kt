package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.ContainerKind

/**
 * Returns `true` when [type] denotes a class Kraft is willing to synthesise
 * a nested mapper for: a concrete user-defined class with a primary
 * constructor. Mirrors the predicate `NestedRule` uses to claim a property,
 * so callers that decide to recurse into a type can be confident the
 * resolver chain will also claim it at runtime.
 */
fun isMappableClass(type: TypeInfo): Boolean {
    val decl = type.declaration
    val fqn = decl.qualifiedName?.asString() ?: return false
    return decl.classKind == ClassKind.CLASS &&
        decl.primaryConstructor != null &&
        Modifier.ABSTRACT !in decl.modifiers &&
        Modifier.SEALED !in decl.modifiers &&
        !fqn.startsWith("kotlin.") &&
        !fqn.startsWith("java.")
}

/**
 * A recognised container type (see [ContainerKind]) with its resolved type arguments.
 *
 * @property kind          The container kind.
 * @property typeArguments The type arguments in declaration order (`[K, V]` for a map). An entry
 *                         is null when the argument cannot be resolved to a class-backed type
 *                         (star projections, type parameters) — those are never auto-mapped.
 */
data class ContainerTypeInfo(
    val kind: ContainerKind,
    val typeArguments: List<TypeInfo?>
) {
    /** The element type of a single-element container, or the value type of a map. */
    val element: TypeInfo? get() = typeArguments.lastOrNull()

    /** The key type of a map; null for single-element containers. */
    val key: TypeInfo? get() = if (kind.isKeyed) typeArguments.firstOrNull() else null
}

/**
 * Returns the container kind and type arguments of [type], or `null` when [type] is not one
 * of the [ContainerKind]s. Recognition alone does not make a pair mappable; see
 * [mappedContainerOf] for the kinds Kraft maps element-wise.
 */
fun containerKindOf(type: TypeInfo): ContainerTypeInfo? {
    val kind = ContainerKind.of(type.declaration.qualifiedName?.asString()) ?: return null
    return ContainerTypeInfo(kind, type.ksType.arguments.map { it.type?.resolve()?.toClassTypeInfo() })
}

/** The container kinds Kraft maps element-wise: every single-element container, and `Map` / `MutableMap` values. */
val MAPPED_CONTAINER_KINDS: Set<ContainerKind> = setOf(
    ContainerKind.LIST,
    ContainerKind.SET,
    ContainerKind.COLLECTION,
    ContainerKind.ITERABLE,
    ContainerKind.ARRAY,
    ContainerKind.MUTABLE_LIST,
    ContainerKind.MUTABLE_SET,
    ContainerKind.MUTABLE_COLLECTION,
    ContainerKind.MAP,
    ContainerKind.MUTABLE_MAP
)

/**
 * The only kinds the deprecated `@MapNested` / explicit `NestedMapping` handling reaches. It is frozen
 * at its 0.13 behaviour, so it never gains the other [MAPPED_CONTAINER_KINDS].
 */
val LEGACY_NESTED_KINDS: Set<ContainerKind> = setOf(ContainerKind.LIST, ContainerKind.SET)

/** [containerKindOf], restricted to the [MAPPED_CONTAINER_KINDS]. */
fun mappedContainerOf(type: TypeInfo): ContainerTypeInfo? =
    containerKindOf(type)?.takeIf { it.kind in MAPPED_CONTAINER_KINDS }

/** [containerKindOf], restricted to the [LEGACY_NESTED_KINDS] the deprecated nested-mapping paths handle. */
fun legacyContainerOf(type: TypeInfo): ContainerTypeInfo? =
    containerKindOf(type)?.takeIf { it.kind in LEGACY_NESTED_KINDS }

/**
 * `true` when [source] and [target] are both [mappedContainerOf] containers, of any kinds:
 * the pairs `ContainerRule` owns (it maps them, or reports why not), and the scalar rules leave alone.
 */
fun isMappedContainerPair(source: TypeInfo, target: TypeInfo): Boolean {
    val sourceKind = mappedContainerOf(source)?.kind ?: return false
    val targetKind = mappedContainerOf(target)?.kind ?: return false
    // A map is never mapped to or from a single-element container.
    return sourceKind.isKeyed == targetKind.isKeyed
}

/** `true` when [source] and [target] are both [legacyContainerOf] containers of the same kind. */
fun isLegacyContainerPair(source: TypeInfo, target: TypeInfo): Boolean {
    val sourceKind = legacyContainerOf(source)?.kind ?: return false
    return sourceKind == legacyContainerOf(target)?.kind
}

/**
 * `true` when converting a [source] container into a [target] container would drop elements:
 * a `Set` / `MutableSet` target fed by anything but a `Set` / `MutableSet` (duplicates vanish).
 * Every other conversion keeps all elements, and so does `Map` to `Map` / `MutableMap`.
 */
fun isLossyContainerConversion(source: ContainerKind, target: ContainerKind): Boolean =
    !source.isKeyed && !target.isKeyed && target in SET_KINDS && source !in SET_KINDS

/**
 * `true` when a [source] container is already a [target] container in Kotlin's type system
 * (`List` is a `Collection`, `MutableSet` is a `Set`), so assigning it needs no copy. Same kind is false:
 * identical types are the direct-match rule's.
 */
fun isContainerSubtype(source: ContainerKind, target: ContainerKind): Boolean = target in SUPERTYPES[source].orEmpty()

private val SET_KINDS = setOf(ContainerKind.SET, ContainerKind.MUTABLE_SET)

private val SUPERTYPES: Map<ContainerKind, Set<ContainerKind>> = mapOf(
    ContainerKind.MUTABLE_MAP to setOf(ContainerKind.MAP),
    ContainerKind.LIST to setOf(ContainerKind.COLLECTION, ContainerKind.ITERABLE),
    ContainerKind.SET to setOf(ContainerKind.COLLECTION, ContainerKind.ITERABLE),
    ContainerKind.COLLECTION to setOf(ContainerKind.ITERABLE),
    ContainerKind.MUTABLE_COLLECTION to setOf(ContainerKind.COLLECTION, ContainerKind.ITERABLE),
    ContainerKind.MUTABLE_LIST to setOf(
        ContainerKind.LIST, ContainerKind.COLLECTION, ContainerKind.ITERABLE, ContainerKind.MUTABLE_COLLECTION
    ),
    ContainerKind.MUTABLE_SET to setOf(
        ContainerKind.SET, ContainerKind.COLLECTION, ContainerKind.ITERABLE, ContainerKind.MUTABLE_COLLECTION
    )
)

private fun KSType.toClassTypeInfo(): TypeInfo? {
    val unwrapped = unwrapTypeAliases()
    if (unwrapped.declaration !is KSClassDeclaration) return null
    return TypeInfo.fromKSType(unwrapped)
}
