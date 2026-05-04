package com.blu3berry.kraft.processor.util

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.blu3berry.kraft.model.TypeInfo
import com.blu3berry.kraft.model.descriptor.CollectionKind

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
 * Returns the [CollectionKind] (`List` or `Set`) of [type], or `null` when
 * [type] is not one of the supported wrapper types. Matches the wrapper set
 * `NestedRule` already recognises — `Map`, `Iterable`, arrays, etc. are
 * intentionally excluded.
 */
fun collectionKindOf(type: TypeInfo): CollectionKind? =
    when (type.declaration.qualifiedName?.asString()) {
        "kotlin.collections.List" -> CollectionKind.LIST
        "kotlin.collections.Set" -> CollectionKind.SET
        else -> null
    }

/**
 * Returns the [TypeInfo] of the first type argument of [type] when it is a
 * recognised single-element collection wrapper (see [collectionKindOf]).
 * Returns `null` for non-collections or for collections whose argument
 * cannot be resolved as a [KSClassDeclaration]-backed type (typically
 * unresolved or projected types — those should not be auto-mapped).
 */
fun elementTypeInfo(type: TypeInfo): TypeInfo? {
    val arg = type.ksType.arguments.firstOrNull() ?: return null
    val argType = arg.type?.resolve() ?: return null
    if (argType.declaration !is KSClassDeclaration) return null
    return TypeInfo.fromKSType(argType)
}
